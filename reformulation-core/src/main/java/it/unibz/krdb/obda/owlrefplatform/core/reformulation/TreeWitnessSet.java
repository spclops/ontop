/*
 * Copyright (C) 2009-2013, Free University of Bozen Bolzano
 * This source code is available under the terms of the Affero General Public
 * License v3.
 * 
 * Please see LICENSE.txt for full license terms, including the availability of
 * proprietary exceptions.
 */
package it.unibz.krdb.obda.owlrefplatform.core.reformulation;

import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.impl.BooleanOperationPredicateImpl;
import it.unibz.krdb.obda.ontology.BasicClassDescription;
import it.unibz.krdb.obda.ontology.Property;
import it.unibz.krdb.obda.owlrefplatform.core.reformulation.QueryConnectedComponent.Edge;
import it.unibz.krdb.obda.owlrefplatform.core.reformulation.QueryConnectedComponent.Loop;
import it.unibz.krdb.obda.owlrefplatform.core.reformulation.TreeWitnessReasonerLite.IntersectionOfConceptSets;
import it.unibz.krdb.obda.owlrefplatform.core.reformulation.TreeWitnessReasonerLite.IntersectionOfProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeWitnessSet {
	private List<TreeWitness> tws = new LinkedList<TreeWitness>();
	private final QueryConnectedComponent cc;
	private final TreeWitnessReasonerLite reasoner;
	private PropertiesCache propertiesCache; 
	private boolean hasConflicts = false;
	
	// working lists (may all be nulls)
	private List<TreeWitness> mergeable;
	private Queue<TreeWitness> delta;
	private Map<TreeWitness.TermCover, TreeWitness> twsCache;

	private static final Logger log = LoggerFactory.getLogger(TreeWitnessSet.class);
	
	private TreeWitnessSet(QueryConnectedComponent cc, TreeWitnessReasonerLite reasoner) {
		this.cc = cc;
		this.reasoner = reasoner;
	}
	
	public Collection<TreeWitness> getTWs() {
		return tws;
	}
	
	public boolean hasConflicts() {
		return hasConflicts;
	}
	
	public static TreeWitnessSet getTreeWitnesses(QueryConnectedComponent cc, TreeWitnessReasonerLite reasoner) {		
		TreeWitnessSet treewitnesses = new TreeWitnessSet(cc, reasoner);
		
		if (!cc.isDegenerate())
			treewitnesses.computeTreeWitnesses();
				
		return treewitnesses;
	}

	private void computeTreeWitnesses() {		
		propertiesCache = new PropertiesCache(reasoner);
		QueryFolding qf = new QueryFolding(propertiesCache); // in-place query folding, so copying is required when creating a tree witness
		
		for (Loop loop : cc.getQuantifiedVariables()) {
			Term v = loop.getTerm();
			log.debug("QUANTIFIED VARIABLE {}", v); 
			qf.newOneStepFolding(v);
			
			for (Edge edge : cc.getEdges()) { // loop.getAdjacentEdges()
				if (edge.getTerm0().equals(v)) {
					if (!qf.extend(edge.getLoop1(), edge, loop))
						break;
				}
				else if (edge.getTerm1().equals(v)) {
					if (!qf.extend(edge.getLoop0(), edge, loop))
						break;
				}
			}
			
			if (qf.isValid()) {
				// tws cannot contain duplicates by construction, so no caching (even negative)
				Collection<TreeWitnessGenerator> twg = getTreeWitnessGenerators(qf); 
				if (twg != null) { 
					// no need to copy the query folding: it creates all temporary objects anyway (including NewLiterals)
					addTWS(qf.getTreeWitness(twg, cc.getEdges()));
				}
			}
		}		
		
		if (!tws.isEmpty()) {
			mergeable = new ArrayList<TreeWitness>();
			Queue<TreeWitness> working = new LinkedList<TreeWitness>();

			for (TreeWitness tw : tws) 
				if (tw.isMergeable())  {
					working.add(tw);			
					mergeable.add(tw);
				}
			
			delta = new LinkedList<TreeWitness>();
			twsCache = new HashMap<TreeWitness.TermCover, TreeWitness>();

			while (!working.isEmpty()) {
				while (!working.isEmpty()) {
					TreeWitness tw = working.poll(); 
					qf.newQueryFolding(tw);
					saturateTreeWitnesses(qf); 					
				}
				
				while (!delta.isEmpty()) {
					TreeWitness tw = delta.poll();
					addTWS(tw);
					if (tw.isMergeable())  {
						working.add(tw);			
						mergeable.add(tw);
					}
				}
			}				
		}
		
		log.debug("TREE WITNESSES FOUND: {}", tws.size());
	}
	
	private void addTWS(TreeWitness tw1) {
		for (TreeWitness tw0 : tws)
			if (!tw0.getDomain().containsAll(tw1.getDomain()) && !tw1.getDomain().containsAll(tw0.getDomain())) {
				if (!TreeWitness.isCompatible(tw0, tw1)) {
					hasConflicts = true;
					log.debug("CONFLICT: {}  AND {}", tw0, tw1);
				}
			}
		tws.add(tw1);
	}
	
	private void saturateTreeWitnesses(QueryFolding qf) { 
		boolean saturated = true; 
		
		for (Edge edge : cc.getEdges()) { 
			Loop rootLoop, internalLoop;
			if (qf.canBeAttachedToAnInternalRoot(edge.getLoop0(), edge.getLoop1())) {
				rootLoop = edge.getLoop0();
				internalLoop = edge.getLoop1();
			}
			else if (qf.canBeAttachedToAnInternalRoot(edge.getLoop1(), edge.getLoop0())) { 
				rootLoop = edge.getLoop1();
				internalLoop = edge.getLoop0();
			}
			else
				continue;
			
			log.debug("EDGE {} IS ADJACENT TO THE TREE WITNESS {}", edge, qf); 

			if (qf.getRoots().contains(internalLoop)) {
				if (qf.extend(internalLoop, edge, rootLoop)) {
					log.debug("    RE-ATTACHING A HANDLE {}", edge);
					continue;
				}	
				else {
					log.debug("    FAILED TO RE-ATTACH A HANDLE {}", edge);
					return;					
				}
			}

			saturated = false; 

			Term rootNewLiteral = rootLoop.getTerm();
			Term internalNewLiteral = internalLoop.getTerm();
			for (TreeWitness tw : mergeable)  
				if (tw.getRoots().contains(rootNewLiteral) && tw.getDomain().contains(internalNewLiteral)) {
					log.debug("    ATTACHING A TREE WITNESS {}", tw);
					saturateTreeWitnesses(qf.extend(tw)); 
				} 
			
			QueryFolding qf2 = new QueryFolding(qf);
			if (qf2.extend(internalLoop, edge, rootLoop)) {
				log.debug("    ATTACHING A HANDLE {}", edge);
				saturateTreeWitnesses(qf2);  
			}	
		}

		if (saturated && qf.hasRoot())  {
			if (!twsCache.containsKey(qf.getTerms())) {
				Collection<TreeWitnessGenerator> twg = getTreeWitnessGenerators(qf); 
				if (twg != null) {
					TreeWitness tw = qf.getTreeWitness(twg, cc.getEdges()); 
					delta.add(tw);
					twsCache.put(tw.getTerms(), tw);
				}
				else
					twsCache.put(qf.getTerms(), null); // cache negative
			}
			else {
				log.debug("TWS CACHE HIT {}", qf.getTerms());
			}
		}
	}
	
	// can return null if there are no applicable generators!
	
	private Collection<TreeWitnessGenerator> getTreeWitnessGenerators(QueryFolding qf) {
		Collection<TreeWitnessGenerator> twg = null;
		log.debug("CHECKING WHETHER THE FOLDING {} CAN BE GENERATED: ", qf); 
		for (TreeWitnessGenerator g : reasoner.getGenerators()) {
			if (!qf.getProperties().contains(g.getProperty())) {
				log.debug("      NEGATIVE PROPERTY CHECK {}", g.getProperty());
				continue;
			}
			else
				log.debug("      POSITIVE PROPERTY CHECK {}", g.getProperty());

			Set<BasicClassDescription> subc = qf.getInternalRootConcepts();
			if ((subc != null) && !g.endPointEntailsAnyOf(subc)) {
				 log.debug("        ENDTYPE TOO SPECIFIC: {} FOR {}", subc, g);
				 continue;			
			}
			else
				 log.debug("        ENDTYPE IS FINE: TOP FOR {}", g);

			boolean failed = false;
			for (TreeWitness tw : qf.getInteriorTreeWitnesses()) 
				if (!g.endPointEntailsAnyOf(tw.getGeneratorSubConcepts())) { 
					log.debug("        ENDTYPE TOO SPECIFIC: {} FOR {}", tw, g);
					failed = true;
					break;
				} 
				else
					log.debug("        ENDTYPE IS FINE: {} FOR {}", tw, g);
				
			if (failed)
				continue;
			
			if (twg == null) 
				twg = new LinkedList<TreeWitnessGenerator>();
			twg.add(g);
			log.debug("        OK");
		}
		return twg;
	}
	
	public CompatibleTreeWitnessSetIterator getIterator() {
		return new CompatibleTreeWitnessSetIterator(tws.size());
	}
	
	public class CompatibleTreeWitnessSetIterator implements Iterator<Collection<TreeWitness>> {
		private boolean isInNext[];
		private boolean atNextPosition = true;
		private boolean finished = false;
		private Collection<TreeWitness> nextSet = new LinkedList<TreeWitness>();

		private CompatibleTreeWitnessSetIterator(int len) {
			isInNext = new boolean[len];
		}

		/**
	     * Returns the next subset of tree witnesses
	     *
	     * @return the next subset of tree witnesses
	     * @exception NoSuchElementException has no more subsets.
	     */
		@Override
		public Collection<TreeWitness> next() {
			if (atNextPosition) {
				atNextPosition = false;
				return nextSet;
			}
			
			while (!isLast()) 
				if (moveToNext()) {
					atNextPosition = false;
					return nextSet;
				}
			finished = true;
			
			throw new NoSuchElementException("The next method was called when no more objects remained.");
	    }

	  	/**
	     * @return <tt>true</tt> if the PowerSet has more subsets.
	     */
		@Override
	  	public boolean hasNext() {
			if (atNextPosition)
				return !finished;
			
			while (!isLast()) 
				if (moveToNext()) {
					atNextPosition = true;
					return true;
				}
			
			return false;
	  	}
		
		private boolean isLast() {
	  		for (int i = 0; i < isInNext.length; i++)
	  			if (!isInNext[i])
	  				return false;
	  		return true;
			
		}
		
		// return true if the next is compatible
		
		private boolean moveToNext() {
		    boolean carry = true;
			for (int i = 0; i < isInNext.length; i++)
				if(!carry)
					break;
				else {
			        carry = isInNext[i];
			        isInNext[i] = !isInNext[i];
				}			

			nextSet.clear();
			int i = 0;
	      	for (TreeWitness tw : tws)
	      		if (isInNext[i++]) {
	      			for (TreeWitness tw0 : nextSet)
	      				if (!TreeWitness.isCompatible(tw0, tw)) 
	      					return false;
	      					
	      			nextSet.add(tw);
	      		}
	      	return true;
		}
		
		/**
	     * @exception UnsupportedOperationException because the <tt>remove</tt>
	     *		  operation is not supported by this Iterator.
	     */
		@Override
		public void remove() {
			throw new UnsupportedOperationException("The PowerSet class does not support the remove method.");
		}
	}
	
	
	static class PropertiesCache {
		private Map<TermOrderedPair, Set<Property>> propertiesCache = new HashMap<TermOrderedPair, Set<Property>>();
		private Map<Term, IntersectionOfConceptSets> conceptsCache = new HashMap<Term, IntersectionOfConceptSets>();

		private final TreeWitnessReasonerLite reasoner;
		
		private PropertiesCache(TreeWitnessReasonerLite reasoner) {
			this.reasoner = reasoner;
		}
		
		public IntersectionOfConceptSets getLoopConcepts(Loop loop) {
			Term t = loop.getTerm();
			IntersectionOfConceptSets subconcepts = conceptsCache.get(t); 
			if (subconcepts == null) {				
				subconcepts = reasoner.getSubConcepts(loop.getAtoms());
				conceptsCache.put(t, subconcepts);	
			}
			return subconcepts;
		}
		
		
		public Set<Property> getEdgeProperties(Edge edge, Term root, Term nonroot) {
			TermOrderedPair idx = new TermOrderedPair(root, nonroot);
			Set<Property> properties = propertiesCache.get(idx);			
			if (properties == null) {
				for (Function a : edge.getBAtoms()) {
					if (a.getPredicate() instanceof BooleanOperationPredicateImpl) {
						log.debug("EDGE {} HAS PROPERTY {} NO BOOLEAN OPERATION PREDICATES ALLOWED IN PROPERTIES", edge, a);
						properties = Collections.EMPTY_SET;
						break;
					}
				}
				if (properties == null) {
					IntersectionOfProperties set = new IntersectionOfProperties();
					for (Function a : edge.getBAtoms()) {
						log.debug("EDGE {} HAS PROPERTY {}",  edge, a);
						if (!set.intersect(reasoner.getSubProperties(a.getPredicate(), !root.equals(a.getTerm(0)))))
							break;
					}
					properties = set.get();
				}	
				propertiesCache.put(idx, properties); // edge.getTerms()
			}
			return properties;
		}
	}
	
	private static class TermOrderedPair {
		private final Term t0, t1;
		private final int hashCode;

		public TermOrderedPair(Term t0, Term t1) {
			this.t0 = t0;
			this.t1 = t1;
			this.hashCode = t0.hashCode() ^ (t1.hashCode() << 4);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof TermOrderedPair) {
				TermOrderedPair other = (TermOrderedPair) o;
				return (this.t0.equals(other.t0) && this.t1.equals(other.t1));
			}
			return false;
		}

		@Override
		public String toString() {
			return "term pair: (" + t0 + ", " + t1 + ")";
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
	}	
	

	public Set<TreeWitnessGenerator> getGeneratorsOfDetachedCC() {		
		Set<TreeWitnessGenerator> generators = new HashSet<TreeWitnessGenerator>();
		
		if (cc.isDegenerate()) { // do not remove the curly brackets -- dangling else otherwise
			IntersectionOfConceptSets subc = reasoner.getSubConcepts(cc.getLoop().getAtoms());
			log.debug("DEGENERATE DETACHED COMPONENT: {}", cc);
			if (!subc.isEmpty()) // (subc == null) || 
				for (TreeWitnessGenerator twg : reasoner.getGenerators()) {
					if ((subc.get() == null) || twg.endPointEntailsAnyOf(subc.get())) {
						log.debug("        ENDTYPE IS FINE: {} FOR {}", subc, twg);
						generators.add(twg);					
					}
					else 
						 log.debug("        ENDTYPE TOO SPECIFIC: {} FOR {}", subc, twg);
				}
		} 
		else {
			for (TreeWitness tw : tws) 
				if (tw.getDomain().containsAll(cc.getVariables())) {
					log.debug("TREE WITNESS {} COVERS THE QUERY",  tw);
					IntersectionOfConceptSets subc = reasoner.getSubConcepts(tw.getRootAtoms());
					if (!subc.isEmpty())
						for (TreeWitnessGenerator twg : reasoner.getGenerators())
							if ((subc.get() == null) || twg.endPointEntailsAnyOf(subc.get())) {
								log.debug("        ENDTYPE IS FINE: {} FOR {}",  subc, twg);
								if (twg.endPointEntailsAnyOf(tw.getGeneratorSubConcepts())) {
									log.debug("        ENDTYPE IS FINE: {} FOR {}",  tw, twg);
									generators.add(twg);					
								}
								else  
									log.debug("        ENDTYPE TOO SPECIFIC: {} FOR {}", tw, twg);
							}
							else 
								 log.debug("        ENDTYPE TOO SPECIFIC: {} FOR {}", subc, twg);
				}
		}
		
		if (!generators.isEmpty()) {
			boolean saturated = false;
			while (!saturated) {
				saturated = true;
				Set<BasicClassDescription> subc = new HashSet<BasicClassDescription>();		
				for (TreeWitnessGenerator twg : generators) 
					subc.addAll(twg.getSubConcepts());
				
				for (TreeWitnessGenerator g : reasoner.getGenerators()) 
					if (g.endPointEntailsAnyOf(subc)) {
						if (generators.add(g))
							saturated = false;
					}		 		
			} 									
		}	
		return generators;
	}
}
