package uk.ac.ebi.bioportal.webservice.client;

import static java.net.URLEncoder.encode;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.buildOntologyClass;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.collectOntoClasses;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.collectOntoClassesFromPagedResult;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.invokeBioportal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.ClassRef;
import uk.ac.ebi.bioportal.webservice.model.Ontology;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.OntologyClassMapping;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation.Annotation;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation.HierarchyEntry;
import uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.CacheBuilder;

/**
 * A simple client to access the REST API of Bioportal APIs.
 * Note that every instance of this class caches all the ontologies it fetches via {@link #getOntology(String)}.
 * 
 * All the HTTP calls in this class are based on {@link BioportalWebServiceUtils#bioportalBaseUrl}.
 * 
 * TODO: it's getting too big, split into components.
 *
 * <dl><dt>date</dt><dd>30 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@SuppressWarnings ( "serial" )
public class BioportalClient
{	
	/**
	 * Items cached from the web service expires after this amount of mins has passed after the first download
	 * Default is 4h
	 */
	public static final String CACHE_TIMEOUT_MINS_PROP_NAME = "uk.ac.ebi.bioportal.cache_timeout";
	
	/**
	 * With the adoption of OWL/RDF, it has become hard to know which URI prefixes the ontologies use to build their
	 * own classes, in several cases, where the ontology uses multiple namespaces, this doesn't even make sense.
	 * 
	 * The mapping above (between acronyms and URI prefixes) corresponds to known cases, most of them are periodically
	 * computed manually, by looking at the results of GetOntoPrefixesTest.
	 * 
	 * This is used in {@link #getOntology(String)}
	 */
	public static final Map<String, String> KNOWN_ONTOLOGY_CLASS_URI_PREFIXES;

	/**
	 * Allows to do the reverse that {@link #KNOWN_ONTOLOGY_CLASS_URI_PREFIXES} allows to do: get an acronym from
	 * a URI prefix.
	 */
	private static final Map<String, String> uri2OntologyMap;
		
	protected final String apiKey; 
	private Map<String, OntologyClass> classCache;
	private Map<String, Ontology> ontologyCache;
	private Map<String, List<OntologyClassMapping>> classMappingsCache;	
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	static
	{
		KNOWN_ONTOLOGY_CLASS_URI_PREFIXES = new HashMap<String, String> () {{
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
			put ( "SIO", "http://semanticscience.org/resource/" );
			put ( "NCBITAXON", "http://purl.bioontology.org/ontology/NCBITAXON/" );
			put ( "UO", "http://purl.obolibrary.org/obo/" );
			put ( "UBERON", "http://purl.obolibrary.org/obo/" );
			put ( "MA", "http://purl.obolibrary.org/obo/" );
			put ( "IAO", "http://purl.obolibrary.org/obo/" );
			put ( "OBI", "http://purl.obolibrary.org/obo/" );
			put ( "BFO", "http://purl.obolibrary.org/obo/" );
			put ( "GO", "http://purl.obolibrary.org/obo/" );
			put ( "HP", "http://purl.obolibrary.org/obo/" );
			put ( "PO", "http://purl.obolibrary.org/obo/" );
			put ( "BTO", "http://purl.obolibrary.org/obo/" );
			put ( "CL", "http://purl.obolibrary.org/obo/" );
			put ( "CLO", "http://purl.obolibrary.org/obo/" );
			put ( "NCBITaxon", "http://purl.obolibrary.org/obo/" );
			put ( "IDO", "http://purl.obolibrary.org/obo/" );
			put ( "CHEBI", "http://purl.obolibrary.org/obo/" );
			put ( "ORDO", "http://www.orpha.net/ORDO/" );
			put ( "OMIM", "http://omim.org/entry/" );
			put ( "MESH", "http://purl.bioontology.org/ontology/MESH/" );
			put ( "LNC", "http://purl.bioontology.org/ontology/LNC/" );
		}};
		
		uri2OntologyMap = new HashMap<> ();
		
		for ( String ontoId: KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.keySet () )
		{
			String uriPrefix = KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.get ( ontoId );
			if ( "http://purl.obolibrary.org/obo/".equals ( uriPrefix ) )
				// This needs a different strategy
				uri2OntologyMap.put ( "http://purl.obolibrary.org/obo/" + ontoId + "_", ontoId );
			else
				uri2OntologyMap.put ( uriPrefix, ontoId );
		}
	} // static class init
	
	
	
	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	public BioportalClient ( String bioportalApiKey )
	{
		long ttl = Long.parseLong ( System.getProperty ( CACHE_TIMEOUT_MINS_PROP_NAME, "" + 60 * 4 ) ); 
		
		CacheBuilder cacheBuilder = CacheBuilder.newBuilder ()
			.maximumSize ( 300000 )
			.expireAfterWrite ( ttl, TimeUnit.MINUTES );

		classCache = cacheBuilder.build ().asMap ();
		ontologyCache = cacheBuilder.build ().asMap ();
		classMappingsCache = cacheBuilder.build ().asMap ();
		
		this.apiKey = bioportalApiKey;
	}

	/**
	 * The details about an ontology class/term.
	 * This is internally cached and a new cache is created upon every new instance of this class.
	 *  
	 * @param accession might be either an accession like EFO_0000001, or a full URI (starting with http://)
	 * @param ontologyAcronym the Bioportal ontology acronym where the term is defined. If this is null and the accession
	 * is a URI, we attempt to get the acronym from kwnon ontologies and the class URI, but it doesn't always work, sorry,
	 * that's a limit of Bioportal and other lookup services.
	 */
	public OntologyClass getOntologyClass ( String ontologyAcronym, String accession )
	{
		try
		{
			if ( accession == null ) throw new IllegalArgumentException (
				"Cannot query Bioportal without term accession/URI" 
			);
			
			String classUri = accession;
			if ( accession.startsWith ( "http://" ) || accession.startsWith ( "https://" ) )
			{
				if ( ontologyAcronym == null )
				{
					// We try to resolve unspecified acronym by means of some heuristics, and using known ontologies.
					// The degree of success varies and it's outrageous that look services demand this parameter, which
					// doesn't even make sense.
					//
					int brkIdx = -1; 
					
					if ( classUri.startsWith ( "http://purl.obolibrary.org/obo/" ) ) brkIdx = classUri.lastIndexOf ( '_' );
					if ( brkIdx == -1 )	brkIdx = classUri.lastIndexOf ( '#' );
					if ( brkIdx == -1 )	brkIdx = classUri.lastIndexOf ( '/' );
					if ( brkIdx != -1 )
					{
						ontologyAcronym = uri2OntologyMap.get ( classUri.substring ( 0, brkIdx + 1 ) );
						if ( ontologyAcronym == null )
						{
							log.debug ( 
								"Cannot get class details for <{}>, unless you specify the defining ontology, returning null",
								classUri
							);
							return null;
						}
					} // brkIdx
				} // null ontologyAcronym
				
				if ( "OMIM".equals ( ontologyAcronym ) )
				{
					// Special case, it wants the final code, not the URI
					String ontoPrefx = KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.get ( ontologyAcronym );
					if ( classUri.startsWith ( ontoPrefx ) )
						classUri = classUri.substring ( ontoPrefx.length () );
				}
			} // http case 
			else
			{
				// accession is not a URI

				if ( !"OMIM".equals ( ontologyAcronym ) )
				{
					String ontoUriPrefix = null;
					Ontology onto = getOntology ( ontologyAcronym );
					if ( onto == null ) return null;
				
					ontoUriPrefix = onto.getClassUriPrefix ();
					if ( ontoUriPrefix == null ) return null;

					classUri = ontoUriPrefix + accession;
				}
			}

			classUri = StringUtils.trimToNull ( classUri );
			if ( classUri == null ) 
			{
				synchronized ( this ) 
				{
					log.error ( "\n\n------------------- BioportalClient, classUri == null! -----------------" );
					log.error ( "accession: '{}', acronym: '{}'", accession, ontologyAcronym );
					log.error ( "KNOWN_ONTOLOGY_CLASS_URI_PREFIXES:\n{}", KNOWN_ONTOLOGY_CLASS_URI_PREFIXES );
					log.error ( "uri2OntologyMap:", uri2OntologyMap );
					log.error ( "\n\n\n" );
				}
				
				throw new IllegalArgumentException ( 
					"Cannot invoke Bioportal with <" + ontologyAcronym + "/" + classUri + ">" 
				);
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
			if ( !"NCBITaxon".equals ( acronym ) ) acronym = acronym.toUpperCase ();
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
			if ( jclasses == null ) return result;
			
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
			throw new IllegalArgumentException ( 
				"Charset error while fetching ontology: '" + acronym + "': " + ex.getMessage (), ex 
			);
		}
	}
	
	
	/**
	 * Calls the <a href = 'http://data.bioontology.org/documentation#nav_annotator'>API for the text annotator</a>.
	 * 
	 * {@link TextAnnotation} and linked classes reflect the JSON structure that the API returns back.
	 * 
	 * @param otherProps is an optional sequence of [name, value, name value...], which are the parameters  
	 * accepted by the annotator API. We suggest that, in order to tune these parameters, you first use the 
	 * <a href = 'https://bioportal.bioontology.org/annotator'>human interface</a> and then click on 'JSON format' on 
	 * the resulting web page. This will show you the selected paramters in the browser URL text box.
	 * 
	 */
	public TextAnnotation[] getTextAnnotations ( String text, String... otherProps )
	{
		TextAnnotation result[];
		String bpParams[];
		
		if ( otherProps != null && otherProps.length > 0 ) 
		{
			// TODO: Use commons
			bpParams = new String [ 2 + otherProps.length ];
			for ( int i = 0; i < otherProps.length; i++ )
			{
				bpParams [ i + 2 ] = otherProps [ i ];
				bpParams [ ++i + 2 ] = otherProps [ i ];
			}
		}
		else
			bpParams = new String [ 2 ];
		
		bpParams [ 0 ] = "text";
		bpParams [ 1 ] = text;
		
		JsonNode jsanns = invokeBioportal ( "/annotator", this.apiKey, bpParams );
		if ( jsanns == null ) return new TextAnnotation [ 0 ];
				
		result = new TextAnnotation[ jsanns.size () ];
		
		int i = 0;
		for ( JsonNode jsann: jsanns )
		{
			{
				JsonNode annClass = jsann.get ( "annotatedClass" );
				String clsIri = annClass.get ( "@id" ).asText ();
				String ontoUri = annClass.get ( "links" ).get ( "ontology" ).asText ();
				String ontoAcronym = ontoUri.substring ( "http://data.bioontology.org/ontologies/".length () );
								
				result [ i ] = new TextAnnotation ( new ClassRef ( clsIri, ontoAcronym ) );
			}
			
			JsonNode jshs = jsann.get ( "hierarchy" );
			HierarchyEntry[] hes = new HierarchyEntry[ jshs.size () ];
			int ih = 0;
			for ( JsonNode jsh: jshs )
			{
				JsonNode annClass = jsh.get ( "annotatedClass" );
				String clsIri = annClass.get ( "@id" ).asText ();
				String ontoUri = annClass.get ( "links" ).get ( "ontology" ).asText ();
				String ontoAcronym = ontoUri.substring ( "http://data.bioontology.org/ontologies/".length () );
				int distance = jsh.get ( "distance" ).asInt ();
				
				hes [ ih++ ] = new HierarchyEntry ( new ClassRef ( clsIri, ontoAcronym ), distance );
			}
			result [ i ].setHierarchy ( hes );

			JsonNode jstanns = jsann.get ( "annotations" );
			Annotation [] textAnns = new Annotation [ jstanns.size () ];
			int ia = 0;
			for ( JsonNode jsa: jstanns )
			{
				textAnns [ ia++ ] = new Annotation (
					jsa.get ( "from" ).asInt (), 
					jsa.get ( "to" ).asInt (), 
					jsa.get ( "matchType" ).asText (),
					jsa.get ( "text" ).asText () 
				);
			}
			result [ i++ ].setAnnotations ( textAnns ); 
		}
		
		return result;
	}
	
	/**
	 * Wrapper with no preferred ontologies.
	 * 
	 * This is the method that contains the API invocation (the other just filters out the results).
	 */
	public List<OntologyClassMapping> getOntologyClassMappings ( OntologyClass ontoClass )
	{
		try
		{
			String clsIri = ontoClass.getIri ();
			List<OntologyClassMapping> result = this.classMappingsCache.get ( clsIri );
			if ( result != null ) return result.isEmpty () ? null : result;
 			
			String ontoId = ontoClass.getOntologyAcronym ().toUpperCase ();
			String servicePath = 
				"/ontologies/" + ontoId  
			  + "/classes/" + URLEncoder.encode ( ontoClass.getIri (), "UTF-8" ) 
			  + "/mappings";
			
			JsonNode jsmaps = invokeBioportal ( servicePath, this.apiKey );
			
			// Shouldn't happen, but just in case
			if ( jsmaps == null  )
			{
				result = Collections.emptyList ();
				this.classMappingsCache.put ( clsIri, result );
				return null;
			}
			
			// Every item in the JSON result contains data on the source of mapping, plus two class IRIs, the first is always
			// the input class (in this case), and the second is the meat we're interested in
			//
			result = new ArrayList<OntologyClassMapping> ();
			
			for ( JsonNode jsmap: jsmaps )
			{
				OntologyClassMapping map = new OntologyClassMapping ();
				
				map.setId ( jsmap.get ( "id" ).asText () );
				map.setSource ( jsmap.get ( "source" ).asText () );
				map.setProcess ( jsmap.get ( "process" ).asText () );
				
				JsonNode jsTargetClass = jsmap.get ( "classes" ).get ( 1 );

				String ontoUri = jsTargetClass.get ( "links" ).get ( "ontology" ).asText ();
				String ontoAcronym = ontoUri.substring ( "http://data.bioontology.org/ontologies/".length () );

				map.setTargetClassRef ( new ClassRef ( 
					jsTargetClass.get ( "@id" ).asText (),
					ontoAcronym
				));
				result.add ( map );
			}
		
			// Too slow to do it again...
			// Possibly empty results are saved to tell the cache we've already tried, null is always returned 
			// for them.
			this.classMappingsCache.put ( clsIri, result );
		
			return result.isEmpty () ? null : result;
		}
		catch ( UnsupportedEncodingException ex )
		{
			throw new OntologyServiceException ( String.format ( 
				"Error while trying to get mappings from %s: %s", ontoClass.getIri (), ex.getMessage () ),
				ex
			);
		}
	}
	
	/**
	 * Invokes the <a href = 'http://data.bioontology.org/documentation#Mapping'>mapping service</a>, telling the 
	 * ontology terms that are associated to the input.
	 * 
	 * @param ontoClass the input class, which of URI and IRI acronym is passed to the mapping REST URL.
	 * @param preferredOntologies a comma-separated list of ontology acronyms, as they're acknowledged by Bioportal. Results
	 * will be filtered using this parameter, if non null 
	 * @param usePreferredOntologiesOnly if this is true and no result falls within preferredOntologies, non-matching
	 * result is returned anyways, else the filtered one only is returned.
	 * 
	 * @return a list of {@link OntologyClassMapping}, or null if no mapping was found.
	 */
	public List<OntologyClassMapping> getOntologyClassMappings ( 
		OntologyClass ontoClass, String preferredOntologies, boolean usePreferredOntologiesOnly 
	)
	{
		List<OntologyClassMapping> maps = this.getOntologyClassMappings ( ontoClass );
		if ( maps == null ) return null;
		
		preferredOntologies = StringUtils.trimToNull ( preferredOntologies );
		if ( preferredOntologies == null ) return maps;
		
		// So, we must check preferred ones from now on
		List<OntologyClassMapping> filteredMaps = new ArrayList<OntologyClassMapping> ();
		for ( OntologyClassMapping map: maps )
		{
			ClassRef clsRef = map.getTargetClassRef ();
			String ontoAcronym = clsRef.getOntologyAcronym ();
			if ( preferredOntologies.contains ( ontoAcronym  ) ) 
				filteredMaps.add ( map );
		}
		
		if ( filteredMaps.isEmpty () ) return usePreferredOntologiesOnly ? null : maps;
		return filteredMaps;
	}
	
}
