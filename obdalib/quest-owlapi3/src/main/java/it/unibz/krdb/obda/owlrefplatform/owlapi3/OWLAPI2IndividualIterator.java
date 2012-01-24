package it.unibz.krdb.obda.owlrefplatform.owlapi3;

import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.ontology.Assertion;
import it.unibz.krdb.obda.ontology.Description;
import it.unibz.krdb.obda.owlapi3.OWLAPI3IndividualTranslator;
import it.unibz.krdb.obda.owlrefplatform.core.abox.VirtualABoxMaterializer;

import java.util.HashMap;
import java.util.Iterator;

import org.semanticweb.owlapi.model.OWLIndividualAxiom;

public class OWLAPI2IndividualIterator implements Iterator<OWLIndividualAxiom> {

	private Iterator<Assertion> assertions = null;

	private OWLAPI3IndividualTranslator translator = new OWLAPI3IndividualTranslator();
	
	public OWLAPI2IndividualIterator(OBDAModel model) throws Exception {
		VirtualABoxMaterializer materializer = new VirtualABoxMaterializer(model, new HashMap<Predicate, Description>());
		Iterator<Assertion> assertions = materializer.getAssertionIterator();
		setAssertions(assertions);
	}
	
	public OWLAPI2IndividualIterator(Iterator<Assertion> assertions) {
		setAssertions(assertions);
	}
	
	public void setAssertions(Iterator<Assertion> assertions) {
		this.assertions = assertions;
	}
	
	@Override
	public boolean hasNext() {
		return assertions.hasNext();
	}

	@Override
	public OWLIndividualAxiom next() {
		Assertion assertion = assertions.next();
		OWLIndividualAxiom individual = translator.translate(assertion);
		return individual;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("This iterator is read-only");
	}
}