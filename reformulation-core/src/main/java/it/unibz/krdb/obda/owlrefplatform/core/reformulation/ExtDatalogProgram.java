/*
 * Copyright (C) 2009-2013, Free University of Bozen Bolzano
 * This source code is available under the terms of the Affero General Public
 * License v3.
 * 
 * Please see LICENSE.txt for full license terms, including the availability of
 * proprietary exceptions.
 */
package it.unibz.krdb.obda.owlrefplatform.core.reformulation;

import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.DatalogProgram;
import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.ontology.BasicClassDescription;
import it.unibz.krdb.obda.ontology.OClass;
import it.unibz.krdb.obda.ontology.Ontology;
import it.unibz.krdb.obda.ontology.Property;
import it.unibz.krdb.obda.ontology.PropertySomeRestriction;
import it.unibz.krdb.obda.owlrefplatform.core.basicoperations.CQCUtilities;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtDatalogProgram {
	private static final OBDADataFactory fac = OBDADataFactoryImpl.getInstance();
	private static final Logger log = LoggerFactory.getLogger(ExtDatalogProgram.class);

	private Ontology sigma;

	private final TreeWitnessReasonerLite reasoner;

	private final Map<Predicate, ExtDatalogProgramDef> extPredicateMap = new HashMap<Predicate, ExtDatalogProgramDef>();
	private final DatalogProgram fullDP;

	public ExtDatalogProgram(TreeWitnessReasonerLite reasoner) {
		this.reasoner = reasoner;
		fullDP = fac.getDatalogProgram();
	}
	
	/**
	 * clears the cache (called when a new CBox is set)
	 */
	
	public void setSigma(Ontology sigma) {
		this.sigma = sigma;
		extPredicateMap.clear();
		fullDP.removeAllRules();
	}

	public DatalogProgram getFullDP() {
		return fullDP;
	}
	
	private final Term x = fac.getVariable("x");			
	private final Term y = fac.getVariable("y");
	private final Term w = fac.getVariableNondistinguished(); 
	
	public Predicate getEntryForPredicate(Predicate p) {
		ExtDatalogProgramDef def = extPredicateMap.get(p);
		if (def == null) {			
			String extName = TreeWitnessRewriter.getIRI(p.getName(), "_EXT");
			if (p.getArity() == 1) {
				Predicate extp = fac.getClassPredicate(extName);		
				def = new ExtDatalogProgramDef(fac.getFunction(extp, x), fac.getFunction(p, x));
				
				// add a rule for each of the sub-concepts
				for (BasicClassDescription c : reasoner.getSubConcepts(p)) {
					if (c instanceof OClass) 
						def.add(fac.getFunction(((OClass)c).getPredicate(), x));
					else {     
						PropertySomeRestriction some = (PropertySomeRestriction)c;
						def.add((!some.isInverse()) ? 
								fac.getFunction(some.getPredicate(), x, w) : fac.getFunction(some.getPredicate(), w, x)); 
					}						
				}
			}
			else  {
				Predicate extp = fac.getObjectPropertyPredicate(extName);
				def = new ExtDatalogProgramDef(fac.getFunction(extp, x, y), fac.getFunction(p, x, y));
				
				// add a rule for each of the sub-roles
				for (Property sub: reasoner.getSubProperties(p, false))
					def.add((!sub.isInverse()) ? 
						fac.getFunction(sub.getPredicate(), x, y) : fac.getFunction(sub.getPredicate(), y, x)); 
			}
	
			def.minimise();			
			// if the reduced datalog program is not trivial
			if (def.dp != null) 
				fullDP.appendRule(def.dp);
			extPredicateMap.put(p, def);
		}
		return def.extPredicate;	
	}	

	/**
	 * class for Datalog program definitions of the Ext_E
	 * implements simplified CQ containment checks
	 * 
	 * @author Roman Kontchakov
	 *
	 */
	
	private class ExtDatalogProgramDef {
		private Predicate extPredicate;
		private final Predicate mainPredicate;
		private final CQIE mainQuery;
		private List<CQIE> dp = new LinkedList<CQIE>();
		
		public ExtDatalogProgramDef(Function extAtom, Function mainAtom) {
			this.mainPredicate = mainAtom.getFunctionSymbol();
			this.extPredicate = extAtom.getFunctionSymbol();
			this.mainQuery = fac.getCQIE(extAtom, mainAtom);
		}
		
		public void add(Function body) {
			if (body.getFunctionSymbol().equals(mainPredicate))
				return;
			
			CQIE query = fac.getCQIE(mainQuery.getHead(), body);
			CQCUtilities cqc = new CQCUtilities(query, sigma);
			if (!cqc.isContainedIn(mainQuery)) 
				dp.add(query);
			else
				log.debug("    CQC CONTAINMENT: {} IN {}", query, mainQuery);
		}
		
		public void minimise() {
			log.debug("DP FOR {} IS {}", extPredicate, dp);
			if (!dp.isEmpty()) {
				dp.add(mainQuery);
				dp = CQCUtilities.removeContainedQueries(dp, true, sigma);
				log.debug("SIMPLIFIED DP FOR {} IS {}", extPredicate, dp);
			}
			// reset if the reduced datalog program is trivial
			if (dp.size() <= 1) {
				dp = null;
				extPredicate = null;
			}
		}
	}	
}
