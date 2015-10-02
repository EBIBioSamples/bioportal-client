package uk.ac.ebi.onto_discovery.bioportal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClientTest;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer.DiscoveredTerm;

/**
 * Some testing for {@link BioportalOntoTermDiscoverer}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>18 Sep 2015</dd>
 *
 */
public class BioportalOntoTermDiscovererTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	@Test
	public void testBasics ()
	{
		BioportalOntoTermDiscoverer discoverer = new BioportalOntoTermDiscoverer ( BioportalClientTest.API_KEY  );
		discoverer.setPreferredOntologies ( "EFO,UBERON,CL,CHEBI,BTO,GO,OBI,MESH,FMA,IAO,HP,BAO,MA,ICD10CM,NIFSTD,DOID,IDO,LOINC,OMIM,SIO,CLO,FHHO" );
		discoverer.setFetchLabels ( true );
		
		// Hack tests of minCallDealy and stats.
		// discoverer.setMinCallDelay ( 3000 );
		// discoverer.setStatsSamplingTime ( 50 );
		
		List<DiscoveredTerm> dterms = discoverer.getOntologyTerms ( "homo sapiens", null );
		
		assertNotNull ( "the onto discoverer returns null!", dterms );
		assertFalse ( "the onto discoverer returns empty!", dterms.isEmpty () );
		
		boolean found = false;
		log.info ( "--- Results from onto discoverer ---- " );
		for ( DiscoveredTerm dterm: dterms )
		{
			log.info ( "{}", dterm );
			found = found || "http://purl.obolibrary.org/obo/NCBITaxon_9606".equals ( dterm.getIri () );
		}
		
		assertTrue ( "onto term discoverer doesn't return NCBITaxon_9606!", found );
		
		// discoverer.getOntologyTerms ( "mus musculus", null );
	}
}
