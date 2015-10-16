package uk.ac.ebi.bioportal.webservice.client;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.Ontology;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.utils.io.IOUtils;

import com.google.code.tempusfugit.concurrency.ConcurrentRule;
import com.google.code.tempusfugit.concurrency.annotations.Concurrent;

/**
 * Tests the functions in {@link BioportalClient}. Note that this uses our API-Key, please do not reuse it and
 * get a new one from the Bioportal site.
 *
 * <dl><dt>date</dt><dd>1 Oct 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioportalClientTest
{
	/**
	 * This is our own Bioportal API key. Please go to the Bioportal web site and get one for you, rather than circulating
	 * this. 
	 */
	public static final String API_KEY = "07732278-7854-4c4f-8af1-7a80a1ffc1bb";

	private static BioportalClient bpcli = new BioportalClient ( API_KEY );
	
	Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Rule
	public ConcurrentRule concurrentRule = new ConcurrentRule ();

	
	@Test
	public void testGetOntology ()
	{
		Ontology efo = bpcli.getOntology ( "EFO" );
		assertEquals ( "Bad name!", "Experimental Factor Ontology", efo.getName () );
		assertEquals ( "Bad uriPrefix!", "http://www.ebi.ac.uk/efo/", efo.getClassUriPrefix () );

		// http://purl.obolibrary.org/obo/
		Ontology go = bpcli.getOntology ( "go" );
		assertEquals ( "Bad name!", "Gene Ontology", go.getName () );
		assertEquals ( "Bad uriPrefix!", "http://purl.obolibrary.org/obo/", go.getClassUriPrefix () );
		
		assertNull ( "Should return null ontology!", bpcli.getOntology ( "RUBBISH123" ) );
	}

	@Test
	public void testGetClass ()
	{
		OntologyClass cls1 = bpcli.getOntologyClass ( "EFO", "EFO_0000270" );
		assertEquals ( "Bad prefLabel!", "asthma", cls1.getPreferredLabel () );
		assertTrue ( "Bad synonym!", cls1.getSynonyms ().contains ( "Hyperreactive airway disease" ) );

		OntologyClass cls2 = bpcli.getOntologyClass ( "GO", "GO_1902084" );
		assertTrue ( "Bad prefLabel!", cls2.getPreferredLabel ().contains ( "fumagillin metabolic process" ));
		assertTrue ( "Bad definition!", cls2.getDefinitions ().contains ( "The chemical reactions and pathways involving fumagillin." ) );

		// Use a URI straight
		assertNotNull ( "URI fetching doesn't work!", bpcli.getOntologyClass ( "EFO", "http://www.ebi.ac.uk/efo/EFO_0000001" ) );
	
		assertNull ( "Should return null term!", bpcli.getOntologyClass ( "RUBBISH123", "FOO-456" ) );
		assertNull ( "Should return null term!", bpcli.getOntologyClass ( "EFO", "BAD-ACC" ) );
	}
	
	@Test
	public void testGetDescendants ()
	{
		Set<OntologyClass> desc = bpcli.getClassDescendants ( "EFO", "EFO_0000684" );
		boolean targetFound = false;
		for ( OntologyClass term: desc )
			if ( "http://www.ebi.ac.uk/efo/EFO_0000571".equals ( term.getIri () ) ) 
			{
				targetFound = true;
				break;
		}
		
		assertTrue ( "Descendant not found!", targetFound );
	}
	
	@Test
	public void testGetChildren ()
	{
		Set<OntologyClass> desc = bpcli.getClassChildren ( "EFO", "EFO_0000684" );
		boolean targetFound = false, unexpectedTargetFound = false;
		for ( OntologyClass term: desc )
			if ( "http://www.ebi.ac.uk/efo/EFO_0000270".equals ( term.getIri () ) ) 
				targetFound = true;
			else if ( "http://www.ebi.ac.uk/efo/EFO_0000571".equals ( term.getIri () ) )
				unexpectedTargetFound = true;
		
		assertTrue ( "Child not found!", targetFound );
		assertFalse ( "Descendant found!", unexpectedTargetFound );
	}

	@Test
	public void testGetAncestors ()
	{
		Set<OntologyClass> desc = bpcli.getClassAncestors ( "EFO", "EFO_0000571" );
		boolean targetFound = false;
		for ( OntologyClass term: desc )
			if ( "http://www.ebi.ac.uk/efo/EFO_0000408".equals ( term.getIri () ) ) 
			{
				targetFound = true;
				break;
		}
		
		assertTrue ( "Ancestor not found!", targetFound );
	}

	@Test
	public void testGetParents ()
	{
		Set<OntologyClass> desc = bpcli.getClassParents ( "EFO", "EFO_0004591" );
		boolean targetFound = false, unexpectedTargetFound = false;
		for ( OntologyClass term: desc )
			if ( "http://www.ebi.ac.uk/efo/EFO_0000270".equals ( term.getIri () ) ) 
				targetFound = true;
			else if ( "http://www.ebi.ac.uk/efo/EFO_0000408".equals ( term.getIri () ) )
				unexpectedTargetFound = true;
		
		assertTrue ( "Parent not found!", targetFound );
		assertFalse ( "Ancestor found!", unexpectedTargetFound );
	}

	
	@Test
	@Ignore ( "Not a real test, used to manually check performance issues" )
	@Concurrent ( count = 20 )
	public void testThroughput () throws IOException
	{
		String[] words = StringUtils.split ( IOUtils.readResource ( this.getClass (), "/text_ann_test_terms.txt" ), "\n" );
		int n = 50, fails = 0;
		for ( int i = 1; i <= n; i++ )
		{
			try
			{
				int iw = RandomUtils.nextInt ( 0, words.length );
				bpcli.getTextAnnotations ( words [ iw ], "longest_only", "true" );
				if ( i % ( n / 4 ) == 0 ) log.info ( "{} % completed", 100d * i / n );
			}
			catch ( OntologyServiceException ex ) 
			{
				fails++;
				log.info ( "Failure: {}", ex.getMessage () );
			}
		}
		log.info ( "The end, fails: {} %", 100d * fails / n );
	}
}
