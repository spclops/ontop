/*
 * Copyright (C) 2009-2013, Free University of Bozen Bolzano
 * This source code is available under the terms of the Affero General Public
 * License v3.
 * 
 * Please see LICENSE.txt for full license terms, including the availability of
 * proprietary exceptions.
 */
package it.unibz.krdb.obda.reformulation.semindex.tests;

import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.ontology.OClass;
import it.unibz.krdb.obda.ontology.Ontology;
import it.unibz.krdb.obda.ontology.OntologyFactory;
import it.unibz.krdb.obda.ontology.PropertySomeRestriction;
import it.unibz.krdb.obda.ontology.impl.OntologyFactoryImpl;
import it.unibz.krdb.obda.owlrefplatform.core.dag.DAG;
import it.unibz.krdb.obda.owlrefplatform.core.dag.DAGChain;
import it.unibz.krdb.obda.owlrefplatform.core.dag.DAGConstructor;
import it.unibz.krdb.obda.owlrefplatform.core.dag.DAGNode;
import junit.framework.TestCase;


public class DAGChainTest extends TestCase {

	private static final OBDADataFactory predicateFactory = OBDADataFactoryImpl.getInstance();
	private static final OntologyFactory descFactory = new OntologyFactoryImpl();

	public void test_simple_isa() {
		Ontology ontology = OntologyFactoryImpl.getInstance().createOntology("");

		Predicate a = predicateFactory.getPredicate("a", 1);
		Predicate b = predicateFactory.getPredicate("b", 1);
		Predicate c = predicateFactory.getPredicate("c", 1);

		OClass ac = descFactory.createClass(a);
		OClass bc = descFactory.createClass(b);
		OClass cc = descFactory.createClass(c);

		ontology.addConcept(ac.getPredicate());
		ontology.addConcept(bc.getPredicate());
		ontology.addConcept(cc.getPredicate());

		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(bc, ac));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(cc, bc));

		DAG res = DAGConstructor.getISADAG(ontology);
		res.clean();
		DAGChain.getChainDAG(res);

		assertTrue(res.get(ac).getDescendants().contains(res.get(bc)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(ac).getDescendants().size(), 2);
		
		assertTrue(res.get(bc).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(bc).getDescendants().size(), 1);
		
		assertEquals(res.get(cc).getDescendants().size(), 0);
	}

	public void test_exists_simple() {
		Ontology ontology = OntologyFactoryImpl.getInstance().createOntology("");

		Predicate a = predicateFactory.getClassPredicate("a");
		Predicate r = predicateFactory.getObjectPropertyPredicate("r");
		Predicate c = predicateFactory.getClassPredicate("c");
		OClass ac = descFactory.createClass(a);
		PropertySomeRestriction er = descFactory.getPropertySomeRestriction(r, false);
		PropertySomeRestriction ier = descFactory.getPropertySomeRestriction(r, true);
		OClass cc = descFactory.createClass(c);

		ontology.addConcept(ac.getPredicate());
		ontology.addConcept(cc.getPredicate());

		ontology.addRole(er.getPredicate());
		ontology.addRole(ier.getPredicate());

		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(er, ac));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(cc, ier));
		
		DAG res = DAGConstructor.getISADAG(ontology);
		for (DAGNode nodes: res.allnodes.values()) {
			System.out.println("---- " + nodes);
		}
		
		DAGChain.getChainDAG(res);

		assertTrue(res.get(ac).getDescendants().contains(res.get(er)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(ac).getDescendants().size(), 3);
		
		assertTrue(res.get(er).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(er).getDescendants().size(), 1);
		
		assertTrue(res.get(ier).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(ier).getDescendants().size(), 1);
		
		assertEquals(res.get(cc).getDescendants().size(), 0);
	}

	public void test_exists_complex() {
		Ontology ontology = OntologyFactoryImpl.getInstance().createOntology("");

		Predicate a = predicateFactory.getPredicate("a", 1);
		Predicate r = predicateFactory.getPredicate("r", 2);
		Predicate c = predicateFactory.getPredicate("c", 1);
		Predicate b = predicateFactory.getPredicate("b", 1);
		Predicate d = predicateFactory.getPredicate("d", 1);

		OClass ac = descFactory.createClass(a);
		PropertySomeRestriction er = descFactory.getPropertySomeRestriction(r, false);
		PropertySomeRestriction ier = descFactory.getPropertySomeRestriction(r, true);
		OClass cc = descFactory.createClass(c);
		OClass bc = descFactory.createClass(b);
		OClass dc = descFactory.createClass(d);

		ontology.addConcept(ac.getPredicate());

		ontology.addConcept(cc.getPredicate());
		ontology.addConcept(bc.getPredicate());
		ontology.addConcept(dc.getPredicate());

		ontology.addRole(er.getPredicate());
		ontology.addRole(ier.getPredicate());

		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(er, ac));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(cc, ier));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(bc, er));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(ier, dc));

		DAG res = DAGConstructor.getISADAG(ontology);
		res.clean();
		DAGChain.getChainDAG(res);

		assertTrue(res.get(ac).getDescendants().contains(res.get(er)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(bc)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(ac).getDescendants().size(), 4);

		assertTrue(res.get(dc).getDescendants().contains(res.get(er)));
		assertTrue(res.get(dc).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(dc).getDescendants().contains(res.get(bc)));
		assertTrue(res.get(dc).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(dc).getDescendants().size(), 4);

		assertTrue(res.get(er).getDescendants().contains(res.get(bc)));
		assertTrue(res.get(er).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(er).getDescendants().size(), 2);

		assertTrue(res.get(ier).getDescendants().contains(res.get(bc)));
		assertTrue(res.get(ier).getDescendants().contains(res.get(cc)));
		assertEquals(res.get(ier).getDescendants().size(), 2);

		assertEquals(res.get(bc).getDescendants().size(), 0);
		assertEquals(res.get(cc).getDescendants().size(), 0);
	}

	public void disbledtest_exists_complex_2() {
		Ontology ontology = OntologyFactoryImpl.getInstance().createOntology("");

		Predicate a = predicateFactory.getPredicate("a", 1);
		Predicate r = predicateFactory.getPredicate("r", 2);

		OClass ac = descFactory.createClass(a);
		PropertySomeRestriction er = descFactory.getPropertySomeRestriction(r, false);
		PropertySomeRestriction ier = descFactory.getPropertySomeRestriction(r, true);

		ontology.addConcept(ac.getPredicate());

		ontology.addRole(er.getPredicate());
		ontology.addRole(ier.getPredicate());

		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(ier, ac));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(ier, er));
		ontology.addAssertion(OntologyFactoryImpl.getInstance().createSubClassAxiom(ac, er));

		DAG res = DAGConstructor.getISADAG(ontology);
		DAGChain.getChainDAG(res);

		assertTrue(res.get(ac).getDescendants().contains(res.get(er)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(ac).getDescendants().contains(res.get(ac)));
		assertEquals(res.get(ac).getDescendants().size(), 3);

		assertTrue(res.get(er).getDescendants().contains(res.get(er)));
		assertTrue(res.get(er).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(er).getDescendants().contains(res.get(ac)));
		assertEquals(res.get(er).getDescendants().size(), 3);

		assertTrue(res.get(ier).getDescendants().contains(res.get(er)));
		assertTrue(res.get(ier).getDescendants().contains(res.get(ier)));
		assertTrue(res.get(ier).getDescendants().contains(res.get(ac)));
		assertEquals(res.get(ier).getDescendants().size(), 3);
	}
}
