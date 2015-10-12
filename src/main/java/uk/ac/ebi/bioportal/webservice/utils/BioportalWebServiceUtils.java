package uk.ac.ebi.bioportal.webservice.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.utils.runcontrol.ChainExecutor;
import uk.ac.ebi.utils.runcontrol.RateLimitedExecutor;
import uk.ac.ebi.utils.runcontrol.StatsExecutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Miscellanea of utilities to access the BioPortal REST web service.
 *
 * <dl><dt>date</dt><dd>30 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioportalWebServiceUtils
{
	/**
	 * Send in a property with this to change the period that we log statistics on issued calls.
	 */
	public static final String STATS_SAMPLING_TIME_PROP_NAME = "uk.ac.ebi.bioportal.stats_sampling_time";

	/**
	 * Should it happen that the server side changes this, you can reflect that here.
	 */
	public static String bioportalBaseUrl = "http://data.bioontology.org";
	
	/**
	 * We provide these, in case you want to tweak their parameters. 
	 */
	public static final RateLimitedExecutor RATE_LIMITING_WRAPPER = new RateLimitedExecutor ( 15 );
	public static final StatsExecutor STATS_WRAPPER = new StatsExecutor ( 
		"BioPortal", Long.parseLong ( System.getProperty ( STATS_SAMPLING_TIME_PROP_NAME, "" + 5 * 60 * 1000 ) ) 
	);
		
	
	private static Logger log = LoggerFactory.getLogger ( BioportalWebServiceUtils.class );

	/**
	 * This is used in {@link #invokeBioportal(String, String, String...)}, We have wrap that call with
	 * both a {@link RateLimitedExecutor rate limit wrapper} and a {@link StatsExecutor statistical reporter}.  
	 * The former is needed because Bioportal's server doesn't like to be hammered at speeds higher than 
	 * 15 calls/sec per process.
	 */
	private static ChainExecutor wrapExecutor = new ChainExecutor (
		RATE_LIMITING_WRAPPER,
		  STATS_WRAPPER
	);  
	
	
	/**
	 * Builds our {@link OntologyClass} representation, using the JSON returned by a Bioportal web service for a class.
	 */
	public static OntologyClass buildOntologyClass ( String ontologyAcronym, JsonNode json )
	{
		OntologyClass result = new OntologyClass ();
		result.setOntologyAcronym ( ontologyAcronym );

		result.setIri ( json.get ( "@id" ).asText () );
		result.setPreferredLabel ( json.get ( "prefLabel" ).asText () );
		result.setObsolete ( json.get ( "obsolete" ).asBoolean ( false )  );
		
		JsonNode jsynonyms = json.get ( "synonym" );
		Set<String> synonyms = new HashSet<> ();
		for ( JsonNode jsynonym: jsynonyms )
			synonyms.add ( jsynonym.asText () );
		result.setSynonyms ( synonyms );
		
		JsonNode jdefs = json.get ( "definition" );
		Set<String> defs = new HashSet<> ();
		for ( JsonNode jdef: jdefs )
			defs.add ( jdef.asText () );
		result.setDefinitions ( defs );

		return result;		
	}
	
	/**
	 * invokes a service that returns a JSON array of ontology classes and build the corresponding set of
	 * {@link OntologyClass}. result is created from scratch if it's null.
	 */
	public static Set<OntologyClass> collectOntoClasses ( Set<OntologyClass> result, String unpagedServicePath, String ontologyAcronym, String apiKey )
	{
		if ( result == null ) result = new HashSet<> ();
		JsonNode jterms = invokeBioportal ( unpagedServicePath, apiKey );
		if ( jterms == null ) return result; 
		for ( JsonNode jterm: jterms )
			result.add ( buildOntologyClass ( ontologyAcronym, jterm ) );
		
		return result;
	}

	/**
	 * Wrapper with result = null
	 */
	public static Set<OntologyClass> collectOntoClasses ( String unpagedServicePath, String ontologyAcronym, String apiKey )
	{
		return collectOntoClasses ( null, unpagedServicePath, ontologyAcronym, apiKey );
	}
	
	/**
	 * Like {@link #collectOntoClasses(String, String, String)}, but this is for those service invocations known to 
	 * return paged results and the /collection array in JSON. 
	 */
	public static Set<OntologyClass> collectOntoClassesFromPagedResult ( Set<OntologyClass> result, String unpagedServicePath, String ontologyAcronym, String apiKey )
	{
		if ( result == null ) result = new HashSet<> ();
		JsonNode json = invokeBioportal ( unpagedServicePath, apiKey );
		if ( json == null ) return result;
		
		JsonNode jpageCount = json.get ( "pageCount" );
		int pageCt = jpageCount == null ? 1 : jpageCount.asInt ( 1 );
		for ( int page = 1; page <= pageCt; page++ )
		{
			if ( page > 1 ) json = invokeBioportal ( unpagedServicePath, apiKey, "page" + page );
			if ( json == null ) continue;
			
			JsonNode jterms = json.get ( "collection" );
			if ( jterms != null ) 
				for ( JsonNode jterm: jterms )
					result.add ( buildOntologyClass ( ontologyAcronym, jterm ) );
		}
		
		return result;
	}

	/**
	 * Wrapper with result = null 
	 */
	public static Set<OntologyClass> collectOntoClassesFromPagedResult ( String unpagedServicePath, String ontologyAcronym, String apiKey )
	{
		return collectOntoClassesFromPagedResult ( null, unpagedServicePath, ontologyAcronym, apiKey );
	}
	
	
	/**
	 * Builds the URL to invoke a Bioportal web service, using its REST API. 
	 * servicePath is appended to {@link #bioportalBaseUrl} and parameters are added.
	 * 
	 * @param servicePath
	 * @param paramValPairs its an array of [ name, value, name, value... ]
	 */
	public static URL getBioPortalUrl ( String servicePath, String... paramValPairs )
	{
		try
		{
			URIBuilder uriBuiler = new URIBuilder ( bioportalBaseUrl + servicePath );
			if ( paramValPairs == null ) return uriBuiler.build ().toURL ();
			
			for ( int i = 0; i < paramValPairs.length - 1; i ++ )
				uriBuiler.addParameter ( paramValPairs [ i ], paramValPairs [ ++i ] );
			
			return uriBuiler.build ().toURL ();
				
		} 
		catch ( URISyntaxException|MalformedURLException ex )
		{
			throw new OntologyServiceException ( "Error while accessing Bioportal: " + ex.getMessage (), ex );
		}
	}

	/**
	 * Invokes a Bioportal web service service. Builds the URL via {@link #getBioPortalUrl(String, String...)}, 
	 * then it appends the apiKey to the HTTP request headers, as well as the JSON 'Accept' header. 
	 */
	public static JsonNode invokeBioportal ( final String servicePath, final String apiKey, final String... paramValPairs )
	{
		final JsonNode[] resultWrapper = new JsonNode [ 1 ];
		wrapExecutor.execute ( new Runnable() {
			@Override
			public void run ()
			{
				try
				{
					URL url = getBioPortalUrl ( servicePath, paramValPairs );
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod ( "GET" );
					conn.setRequestProperty ( "Authorization", "apikey token=" + apiKey );
					conn.setRequestProperty ( "Accept", "application/json" );
					
					ObjectMapper mapper = new ObjectMapper ();
					resultWrapper [ 0 ] = mapper.readTree ( conn.getInputStream () );
				}
				catch ( FileNotFoundException ex )
				{
					// This is tricky, since it's not always a real error, e.g., when we try to see if a given class exists
					// we'll get a file-not-found error, because the HTTP query will be formed with an ID in the path that
					// doesn't lead to any path considered valid (the class doesn't exist)
					//
					if ( log.isTraceEnabled () )
						log.trace ( "FileNotFound from '" + servicePath + "', returning null", ex );
					else
						log.debug ( "FileNotFound from '" + servicePath + "', returning null" );
					
					resultWrapper [ 0 ] = null;
				}
				catch ( IOException ex )
				{
					throw new OntologyServiceException ( "Error while accessing Bioportal: " + ex.getMessage (), ex );
				} 
			}
		});
		
		return resultWrapper [ 0 ];
	}
}
