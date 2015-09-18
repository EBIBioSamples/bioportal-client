package uk.ac.ebi.bioportal.webservice.client;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.model.TextAnnotation;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 Aug 2015</dd>
 *
 */
public class TextAnnotatorTest
{
	private static BioportalClient bpcli = new BioportalClient ( BioportalClientTest.API_KEY );

	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Test
	public void testBasics ()
	{
		TextAnnotation[] tas = bpcli.getTextAnnotations ( "homo sapiens" );
		assertTrue ( "No text annotation from Bioportal annotator!", tas.length > 0 );
		
		log.info ( "------ Bioportal results ------" );
		boolean found = false;
		for ( TextAnnotation ta: tas )
		{
			log.info ( "Returned text annotation: {}", ta );
			found = found || "http://purl.obolibrary.org/obo/NCBITaxon_9605".equals ( ta.getAnnotatedClass ().getClassIri () );
		}
		
		assertTrue ( "the text annotator doesn't return NCBITaxon_9605!", found );
	}
	
	@Test
	public void testConstrainedOntologies ()
	{
		// OBI,MESH,FMA,IAO,HP,BAO,MA,ICD10CM,NIFSTD,DOI,IDO,LOINC,OMIM,SIO,CLO,FHHO
		TextAnnotation[] tas = bpcli.getTextAnnotations ( "mus musculus", 
			"ontologies", "EFO,UBERON,CL,CHEBI,BTO,GO", "longest_only", "true" 
		);
		assertTrue ( "No text annotation from Bioportal annotator (with 'ontologies' parameter)!", tas.length > 0 );
		
		log.info ( "------ Bioportal results ------" );
		boolean found = false;
		for ( TextAnnotation ta: tas )
		{
			log.info ( "Returned text annotation: {}", ta );
			found = found || "http://purl.obolibrary.org/obo/NCBITaxon_10088".equals ( ta.getAnnotatedClass ().getClassIri () );
		}
		
		assertTrue ( "the text annotator doesn't return NCBITaxon_10088 (with 'ontologies' parameter)!", found );
	}

}
