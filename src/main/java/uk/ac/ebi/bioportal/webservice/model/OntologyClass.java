package uk.ac.ebi.bioportal.webservice.model;

import java.util.Set;

/**
 * A simple model of an ontology class (aka term), as it is represented by the Bioportal web service.
 *
 * <dl><dt>date</dt><dd>30 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntologyClass
{
	private String iri;
	private String preferredLabel;
	private String ontologyAcronym;
	private Set<String> synonyms, definitions;
	private boolean isObsolete = false;

	public OntologyClass ()
	{
		super ();
	}

	public OntologyClass ( String iri )
	{
		super ();
		this.iri = iri;
	}


	public String getIri ()
	{
		return iri;
	}

	public void setIri ( String iri )
	{
		this.iri = iri;
	}

	public String getPreferredLabel ()
	{
		return preferredLabel;
	}

	public void setPreferredLabel ( String preferredLabel )
	{
		this.preferredLabel = preferredLabel;
	}

	public String getOntologyAcronym ()
	{
		return ontologyAcronym;
	}

	public void setOntologyAcronym ( String ontologyAcronym )
	{
		this.ontologyAcronym = ontologyAcronym;
	}

	public Set<String> getSynonyms ()
	{
		return synonyms;
	}

	public void setSynonyms ( Set<String> synonyms )
	{
		this.synonyms = synonyms;
	}

	public Set<String> getDefinitions ()
	{
		return definitions;
	}

	public void setDefinitions ( Set<String> definitions )
	{
		this.definitions = definitions;
	}

	public boolean isObsolete ()
	{
		return isObsolete;
	}

	public void setObsolete ( boolean isObsolete )
	{
		this.isObsolete = isObsolete;
	}
	
}
