package uk.ac.ebi.bioportal.webservice.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.OntologyClassMapping;
import uk.ac.ebi.utils.time.XStopWatch;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>13 Jan 2016</dd></dl>
 *
 */
public class MappingTest
{
	private static BioportalClient bpcli = new BioportalClient ( BioportalClientTest.API_KEY );
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	
	@Test
	public void testClassMappings ()
	{
		OntologyClass cls = bpcli.getOntologyClass ( "EFO", "http://www.ebi.ac.uk/efo/EFO_0000270" );
		List<OntologyClassMapping> mappings = bpcli.getOntologyClassMappings ( cls );
		
		assertNotNull ( "Null mappings for " + cls.getIri (), mappings );
		
		boolean termRID5327Found = false; 
		boolean termNLMVSFound = false;
		boolean loomFound = false;
		
		log.info ( "got the following for {}:", cls.getIri () );

		for ( OntologyClassMapping mapping: mappings )
		{
			log.info ( mapping.toString () );
			String targetIri = mapping.getTargetClassRef ().getClassIri ();
			termRID5327Found |= "http://www.owl-ontologies.com/Ontology1447432460.owl#RID5327".equals ( targetIri );
			termNLMVSFound |= "http://purl.bioontology.org/ontology/NLMVS/2.16.840.1.113883.3.526.3.362".equals ( targetIri );
			loomFound |= "LOOM".equals ( mapping.getSource () );			
		}
		
		assertTrue ( "RID5327 mapping not found!", termRID5327Found );
		assertTrue ( "NLMVS mapping not found!", termNLMVSFound );
		assertTrue ( "LOOM source in the mappings not found!", loomFound );
	}
	
	@Test
	public void testClassMappingsCache ()
	{
		OntologyClass cls = bpcli.getOntologyClass ( "RADLEX", "http://www.owl-ontologies.com/Ontology1447432460.owl#RID5327" );

		XStopWatch timer = new XStopWatch ();
		timer.start ();
		List<OntologyClassMapping> mappings = bpcli.getOntologyClassMappings ( cls );
		long time0 = timer.getTime ();
		
		assertNotNull ( "null mappings for " + cls.getIri () + "!", mappings );

		timer.restart ();
		int ncalls = 10000;
		for ( int i = 0; i < ncalls; i++ )
		{
			mappings = bpcli.getOntologyClassMappings ( cls );
		}
		timer.stop ();
		
		long time1 = timer.getTime ();
		
		log.info ( "Initial time: {}, next {} calls total time: {}", time0, ncalls, time1 );
		assertTrue ( "WTH?! The mappings cache seems not to be working!", time0 > 100d * time1 / ncalls );
	}
	
	@Test
	public void testPreferredOntologies ()
	{
		OntologyClass cls = bpcli.getOntologyClass ( "EFO", "http://www.ebi.ac.uk/efo/EFO_0000270" );
		String preferredOntologies = "MESH,OMIM"; 
		List<OntologyClassMapping> mappings = bpcli.getOntologyClassMappings ( cls,  "MESH,OMIM", true );
		
		assertNotNull ( "Null mappings for " + cls.getIri (), mappings );
		assertFalse ( "Empty mappings result!", mappings.isEmpty () );
		
		log.info ( "got the following for {}:", cls.getIri () );

		boolean allFromPreffered = true;
		for ( OntologyClassMapping mapping: mappings )
		{
			log.info ( mapping.toString () );
			allFromPreffered &= preferredOntologies.contains ( mapping.getTargetClassRef ().getOntologyAcronym () );
		}
		
		assertTrue ( "Got results from non-preferred ontologies!", allFromPreffered );
	}

}
