package uk.ac.ebi.bioportal.webservice.client;

import static java.lang.System.out;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import uk.ac.ebi.utils.io.IOUtils;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>24 Feb 2015</dd>
 *
 */
public class ProxyTest
{
	@Test
	public void testProxy () throws MalformedURLException, IOException
	{
		out.println ( "------- http.proxyHost = " + System.getProperty ( "http.proxyHost" ) );
		out.println ( "------- http.proxyPort = " + System.getProperty ( "http.proxyPort" ) );
		
		out.println ( "------- From Remote ------" );
		out.println (
			IOUtils.readInputFully ( 
				new InputStreamReader ( new URL ( "http://www.google.com" ).openStream () ) 
			).substring ( 0, 400 ) 
		);
		out.println ( "------- ------\n\n" );
	}
}
