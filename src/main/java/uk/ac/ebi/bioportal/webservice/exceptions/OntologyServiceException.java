package uk.ac.ebi.bioportal.webservice.exceptions;

/**
 * Thrown whenever the invoation of Bioportal web service goes wrong.
 *
 * <dl><dt>date</dt><dd>30 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntologyServiceException extends RuntimeException
{
	private static final long serialVersionUID = 2896736602665226755L;

	public OntologyServiceException ( String message, Throwable cause )
	{
		super ( message, cause );
	}

	public OntologyServiceException ( String message )
	{
		super ( message );
	}
	
}
