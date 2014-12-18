package uk.ac.ebi.bioportal.webservice.client;

import static java.net.URLEncoder.encode;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.buildOntologyClass;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.collectOntoClasses;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.collectOntoClassesFromPagedResult;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.invokeBioportal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.Ontology;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils;
import uk.ac.ebi.utils.memory.SimpleCache;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A simple client to access the REST API of Bioportal APIs.
 * Note that every instance of this class caches all the ontologies it fetches via {@link #getOntology(String)}.
 * 
 * All the HTTP calls in this class are based on {@link BioportalWebServiceUtils#bioportalBaseUrl}.  
 *
 * <dl><dt>date</dt><dd>30 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioportalClient
{	
	/**
	 * With the adoption of OWL/RDF, it has become hard to know which URI prefixes the ontologies use to build their
	 * own classes, in several cases, where the ontology uses multiple namespaces, this doesn't even make sense.
	 * 
	 * The mapping above (between acronyms and URI prefixes) corresponds to known cases, most of them are periodically
	 * computed manually, by looking at the results of GetOntoPrefixesTest.
	 * 
	 * This is used in {@link #getOntology(String)}
	 */
	@SuppressWarnings ( "serial" )
	public static final Map<String, String> KNOWN_ONTOLOGY_CLASS_URI_PREFIXES = new HashMap<String, String> () {{
		put ( "EFO", "http://www.ebi.ac.uk/efo/" );
		put ( "TEO", "http://informatics.mayo.edu/TEO.owl#" );
		put ( "HIVO0004", "http://bioportal/bioontology.org/ontologies/HIVO0004#" );
		put ( "BP-METADATA", "http://protege.stanford.edu/ontologies/metadata/BioPortalMetadata.owl#" );
		put ( "PEO", "http://knoesis.wright.edu/ParasiteExperiment.owl#" );
		put ( "CCON", "http://cerrado.linkeddata.es/ecology/ccon#" );
		put ( "IDODEN", "http://purl.bioontology.org/ontology/" );
		put ( "BRIDG", "http://www.bridgmodel.org/owl#" );
		put ( "ICD11-BODYSYSTEM", "http://who.int/bodysystem.owl#" );
		put ( "AERO", "http://purl.obolibrary.org/obo/" );
		put ( "ONLIRA", "http://vavlab.ee.boun.edu.tr/carera/onlira.owl#" );
		put ( "OGI", "http://purl.obolibrary.org/obo/OGI.owl#" );
		put ( "PROVO", "http://www.w3.org/ns/prov#" );
		put ( "NEOMARK3", "http://www.neomark.eu/ontologies/neomark.owl#" );
		put ( "NEOMARK4", "http://neomark.owl#" );
		put ( "MIXS", "http://gensc.org/ns/mixs/" );
		put ( "CTONT", "http://epoch.stanford.edu/ClinicalTrialOntology.owl#OperationalPlan" );
		put ( "BAO", "http://www.bioassayontology.org/bao#" );
	}};
	
	protected final String apiKey; 
	private SimpleCache<String, OntologyClass> classCache = new SimpleCache<> ( 300000 );
	private SimpleCache<String, Ontology> ontologyCache = new SimpleCache<> ( 300000 );

	
	public BioportalClient ( String bioportalApiKey )
	{
		super ();
		this.apiKey = bioportalApiKey;
	}

	/**
	 * The details about an ontology class/term.
	 * This is internally cached and a new cache is created upon every new instance of this class.
	 *  
	 * @param accession might be either an accession like EFO_0000001, or a full URI (starting with http://)
	 */
	public OntologyClass getOntologyClass ( String ontologyAcronym, String accession )
	{
		try
		{
			String classUri = null;
			if ( accession.startsWith ( "http://" ) )
				classUri = accession;
			else
			{
				Ontology onto = getOntology ( ontologyAcronym );
				if ( onto == null ) return null;
				
				String ontoUriPrefix = onto.getClassUriPrefix ();
				if ( ontoUriPrefix == null ) return null;

				classUri = ontoUriPrefix + accession;
			}
			
			synchronized ( classUri.intern () )
			{
				OntologyClass result = classCache.get ( classUri );
				if ( result != null )
				{
					if ( "".equals ( result.getIri () ) ) return null;
					return result;
				}
				
				JsonNode jclass = invokeBioportal ( 
					"/ontologies/" + encode ( ontologyAcronym.toUpperCase (), "UTF-8" ) + "/classes/" +	encode ( classUri, "UTF-8" ),
					this.apiKey
				);
	
				result = jclass == null ? null : buildOntologyClass ( ontologyAcronym, jclass );
				if ( result == null ) {
					result = new OntologyClass ( "" );
					classCache.put ( classUri, result );
					return null;
				}
				
				classCache.put ( classUri, result );

				return result;
			} // synchronized ( classUri )
		} 
		catch ( UnsupportedEncodingException ex )
		{
			throw new OntologyServiceException ( String.format ( 
				"Error while trying to get info about '%s:%s': %s", ontologyAcronym, accession, ex.getMessage () ),
				ex
			);
		}
	}
	
	/**
	 * It performs the invocation of the webservice: /ontologies/:onto/classes/:classUri/:collectionTypeId, 
	 * where collectionTypeId is something like 'children', 'ancestors' etc. In other words, this gets 
	 * terms associated to the parameter. Bioportal returns some JSON results as paged 
	 * (see {@link BioportalWebServiceUtils#collectOntoClassesFromPagedResult(Set, String, String, String)}) and others
	 * are simple arrays of classes (see {@link BioportalWebServiceUtils#collectOntoClasses(Set, String, String, String)}).
	 * That's what the isPaged parameter is for. See the implementation of methods below, to get an idea of how this one is
	 * used.
	 * 
	 * @param accession might be either an accession like EFO_0000001, or a full URI (starting with http://)
	 */
	private Set<OntologyClass> getClassCollection ( String ontologyAcronym, String accession, String collectionTypeId, boolean isPaged )
	{
		try
		{
			String classUri = null;
			if ( accession.startsWith ( "http://" ) )
				classUri = accession;
			else
			{
				Ontology onto = getOntology ( ontologyAcronym );
				if ( onto == null ) return null;
				
				String ontoUriPrefix = onto.getClassUriPrefix ();
				if ( ontoUriPrefix == null ) return null;

				classUri = ontoUriPrefix + accession;
			}

			String servicePath = 
				"/ontologies/" + encode ( ontologyAcronym.toUpperCase (), "UTF-8" ) + "/classes/" +	URLEncoder.encode ( classUri, "UTF-8" ) +
				"/" + collectionTypeId;
			
			return isPaged  
				? collectOntoClassesFromPagedResult ( servicePath, ontologyAcronym, this.apiKey )
				: collectOntoClasses ( servicePath, ontologyAcronym, this.apiKey );
		} 
		catch ( UnsupportedEncodingException ex )
		{
			throw new OntologyServiceException ( String.format ( 
				"Error while trying to get /%s about '%s:%s': %s", collectionTypeId, ontologyAcronym, accession, ex.getMessage () ),
				ex
			);
		}		
	}
	
	/**
	 * The children (ie, direct subclasses) of the current parameter (an accession or a URI), as returned by the 
	 * web service /ontologies/:onto/classes/:classUri/children 
	 */
	public Set<OntologyClass> getClassChildren ( String ontologyAcronym, String accession )
	{
		return getClassCollection ( ontologyAcronym, accession, "children", true );
	}
	
	/**
	 * The descendants (ie, transitively subclasses) of the current parameter (an accession or a URI), as returned by the 
	 * web service /ontologies/:onto/classes/:classUri/descendants 
	 */
	public Set<OntologyClass> getClassDescendants ( String ontologyAcronym, String accession )
	{
		return getClassCollection ( ontologyAcronym, accession, "descendants", true );
	}
	
	/**
	 * The ancestors (ie, transitively super classes) of the current parameter (an accession or a URI), as returned by the 
	 * web service /ontologies/:onto/classes/:classUri/ancestors 
	 */
	public Set<OntologyClass> getClassAncestors ( String ontologyAcronym, String accession )
	{
		return getClassCollection ( ontologyAcronym, accession, "ancestors", false );
	}

	/**
	 * The parents (ie, direct super classes) of the current parameter (an accession or a URI), as returned by the 
	 * web service /ontologies/:onto/classes/:classUri/parents 
	 */
	public Set<OntologyClass> getClassParents ( String ontologyAcronym, String accession )
	{
		return getClassCollection ( ontologyAcronym, accession, "parents", false );
	}
	
	// TODO: tree, pathsToRoot
	
	
	/**
	 * Gets information about an ontology, by invoking the /ontologies web service in BioPortal. 
	 * The result is cached internally. 
	 * 
	 * Note that {@link Ontology#getClassUriPrefix()} is computed by first looking at {@link #KNOWN_ONTOLOGY_CLASS_URI_PREFIXES}
	 * and, if no entry exists for the current ontlogy, a guess is done by looking at the URI of the first class reported
	 * by the ontology (ie, using the service /ontologies/:acronym/classes).
	 * 
	 * This is an heuristic approach and we cannot guarantee such prefix always exists or even makes sense.
	 * 
	 */
	public Ontology getOntology ( String acronym )
	{
		try
		{
			acronym = acronym.toUpperCase ();
			String encodedAcronym = encode ( acronym, "UTF-8" );
			Ontology result;
			
			synchronized ( acronym.intern () )
			{
				result = this.ontologyCache.get ( acronym );
				// We store null results, to avoid further searches
				if ( result != null ) return "__NULL_ONTO__".equals ( result.getAcronym () ) ? null : result;
						
				JsonNode jonto = BioportalWebServiceUtils.invokeBioportal ( "/ontologies/" + encodedAcronym, this.apiKey );
				if ( jonto == null ) 
				{
					this.ontologyCache.put ( acronym, new Ontology ( "__NULL_ONTO__" ) );
					return null;
				}
				
				result = new Ontology ( acronym );
				result.setName ( jonto.get ( "name" ).asText () );
				this.ontologyCache.put ( acronym, result );
			}
			
			// Gets the likely URI prefix for building the URI of ontology terms.
			// 
			String classUriPrefix = KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.get ( acronym ); // Is it already known?
			if ( classUriPrefix != null ) 
			{
				result.setClassUriPrefix ( classUriPrefix );
				return result;
			}
			// If not, try with the first ontology class
			JsonNode jclasses = invokeBioportal ( "/ontologies/" + encodedAcronym + "/classes", this.apiKey, "pagesize", "2" );
			String classUri = jclasses.at ( "/collection/0/@id" ).asText ();
			if ( classUri == null ) return result;
			
			// Try to remove the trailing accession, by looking at common splitters 
			int brkIdx = classUri.lastIndexOf ( '#' );
			if ( brkIdx == -1 )	brkIdx = classUri.lastIndexOf ( '/' );
			if ( brkIdx == -1 ) return result;
			
			// Got it!
			classUriPrefix = classUri.substring ( 0, brkIdx + 1 );
			result.setClassUriPrefix ( classUriPrefix );

			return result;
		} 
		catch ( UnsupportedEncodingException ex )
		{
			throw new IllegalArgumentException ( "Charset error while fetching ontology: '" + acronym + "': " + ex.getMessage (), ex );
		}
	}
}
