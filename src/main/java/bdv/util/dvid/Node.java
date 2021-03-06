package bdv.util.dvid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.imglib2.util.Pair;
import bdv.util.http.HttpRequest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 * Node class represents dvid node/checkout. (A checkout is
 * a node in the version history graph).
 *
 */
public class Node
{

	private final String uuid;
	private final Repository repository;

	/**
	 * @return uuid of node/commit
	 */
	public String getUuid()
	{
		return uuid;
	}

	/**
	 * TODO Not implemented yet
	 * @return Parent commit.
	 */
	public Node getParent()
	{
		return null; // null for now
	}

	/**
	 * @return {@link Repository} to which {@link Node} instance belongs.
	 */
	public Repository getRepository()
	{
		return repository;
	}

	/**
	 * @return Base url of this node/commit.
	 */
	public String getUrl()
	{
		return repository.getServer().getApiUrl() +"/node/" + this.uuid;
	}

	/**
	 *
	 * Commit and fix a note. No changes can be applied to this note after
	 * a commit (need to {@link Node#branch}).
	 *
	 * @param note Commit note.
	 * @param log Commit log (see dvid documentation).
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void commit( final String note, final String[] log ) throws MalformedURLException, IOException
	{
		final JsonArray arr = new JsonArray();

		for ( final String l : log )
			arr.add(  new JsonPrimitive( l ) );

		final JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		json.add( "log", arr );
		final String url = getUrl() + "/commit";
		HttpRequest.postRequestJSON( url, json );
	}

	/**
	 *
	 * Create new branch based on the {@link Node} instance.
	 *
	 * @param note Branch note.
	 * @return {@link Node} associated with the new branch.
	 * @throws MalformedURLException
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public Node branch( final String note ) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		final JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		final String url = DvidUrlOptions.getRequestString( getUrl() + "/branch" );
		final HttpURLConnection connection = HttpRequest.postRequestJSON( url, json );
		final JsonObject response = new Gson().fromJson( new InputStreamReader( connection.getInputStream() ), JsonObject.class );
		connection.disconnect();
		return new Node( response.get( "child" ).getAsString(), this.repository );
	}

	public Node( final String uuid, final Repository repository )
	{
		super();
		this.uuid = uuid;
		this.repository = repository;
	}

	/**
	 *
	 * Create data set based on name and type. Cast return value to appropriate type, e.g.:
	 * {@link Node} node = // initialize
	 * {@link DatasetBlkLabel} ds = (DatasetBlkLabel)node.createDataset( "name", DatasetBlkLabel.TYPE );
	 *
	 * @param name Name of the data set.
	 * @param type Type of the data set. If type is known, an appropriate class will be chosen for data
	 * set creation. Otherwise, use {@link DataSet}.
	 * @param sync List of data set names that the new data set will be synched with.
	 * @return {@link Dataset} instance referring to a dvid dataset with name name and type type at
	 * commit represented by the {@link Node} instance.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Dataset createDataset( final String name, final String type, final String... sync ) throws MalformedURLException, IOException
	{
		final String postUrl = new StringBuilder( repository.getServer().getApiUrl() )
			.append( "/repo/" )
			.append( this.uuid )
			.append( "/instance" )
			.toString()
			;

		final JsonObject json = new JsonObject();
		json.addProperty( Dataset.POPERTY_DATANAME, name );
		json.addProperty( Dataset.PROPERTY_TYPENAME, type );
		if ( sync.length > 0 )
		{
			final StringBuilder syncString = new StringBuilder( sync[ 0 ] );
			for ( int i = 1; i < sync.length; ++i )
			{
				syncString
					.append( "," )
					.append( sync[ i ] )
					;
			}
			json.addProperty( Dataset.PROPERTY_SYNC, syncString.toString() );
		}

		HttpRequest.postRequestJSON( DvidUrlOptions.getRequestString( postUrl ), json ).disconnect();

		if ( type.compareToIgnoreCase( DatasetKeyValue.TYPE) == 0 )
			return new DatasetKeyValue( this, name );

		else if ( type.compareToIgnoreCase( DatasetBlkLabel.TYPE ) == 0 )
			return new DatasetBlkLabel( this, name );

		else if ( type.compareToIgnoreCase( DatasetLabelVol.TYPE ) == 0 )
			return new DatasetLabelVol( this, name );

		else if ( type.compareToIgnoreCase(  DatasetBlkUint8.TYPE ) == 0 )
			return new DatasetBlkUint8( this, name );

		else if ( type.compareToIgnoreCase( DatasetBlkRGBA.TYPE ) == 0 )
			return new DatasetBlkRGBA( this, name );

		else
			return new Dataset( this, name, type );
	}

	/**
	 *
	 * Create a set of data sets that are all mutually synched.
	 *
	 * @param namesAndTypes List holding names and types of data sets to be created.
	 * @return List ({@link Dataset[]}) of the newly created data sets.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Dataset[] createMutuallySynchedDatasets( final List< Pair< String, String > > namesAndTypes ) throws MalformedURLException, IOException
	{
		final int length = namesAndTypes.size();
		final Dataset[] datasets = new Dataset[ length ];
		for ( int i = 0; i < length; ++i )
		{
			final String[] sync = new String[ length - 1 ];
			final Pair< String, String > pair = namesAndTypes.get( i );
			final ArrayList< String > tmp = new ArrayList< String >();
			for ( int k = 0; k < length; ++k )
				if ( k != i )
					tmp.add( namesAndTypes.get( k ).getA() );
				else
					continue;

			tmp.toArray( sync );
			datasets[ i ] = createDataset( pair.getA(), pair.getB(), sync );

		}
		return datasets;
	}

	/**
	 *
	 * Compare uuids of two nodes. Compare only the first n characters, where
	 * n is the minimum of the lengths of the uuids.
	 *
	 * @param uuid1 first uuid
	 * @param uuid2 second uuid
	 * @return  the value {@code 0} if both uuids are equivalent;
	 * a value less than {@code 0} if uuid1 is lexicographically less than uuid2;
	 * and a value greater than {@code 0} if uuid1 is lexicographically greater
	 * than uuid2.
	 */
	public static int compareUuids( final String uuid1, final String uuid2 )
	{
		final int length = Math.min( uuid1.length(), uuid2.length() );
		return uuid1.substring( 0, length ).compareTo( uuid2.substring( 0, length ) );
	}

	/**
	 *
	 * Check if two uuids are equivalent.
	 *
	 * @param uuid1 first uuid
	 * @param uuid2 second uuid
	 * @return true if equiovalent, false otherwise
	 */
	public static boolean uuidEquivalenceCheck( final String uuid1, final String uuid2 )
	{
		return compareUuids( uuid1, uuid2 ) == 0;
	}

	/**
	 *
	 * Delete data set by name.
	 *
	 * @param name Name of the data set to be deleted.
	 * @return Http status code of the DELETE request.
	 * @throws IOException
	 */
	public int deleteDataset( final String name ) throws IOException
	{
		// /api/repo/{uuid}/{dataname}?imsure=true
		final HashMap< String, String > deleteOptions = new HashMap< String, String >();
		deleteOptions.put( "imsure", "true" );
		final String url = DvidUrlOptions.getRequestString(
				this.getRepository().getServer().getApiUrl() + "/repo/" + this.uuid + "/" + name, "", deleteOptions
				);
		return HttpRequest.delete( url );
	}

	/**
	 *
	 * Delete data set by {@link Dataset}.
	 *
	 * @param dataset Data set to be deleted.
	 * @return Http status code of the DELETE request.
	 * @throws IOException
	 */
	public int deleteDatset( final Dataset dataset ) throws IOException
	{
		return deleteDataset( dataset.name );
	}

	public static void main( final String[] args ) throws MalformedURLException, IOException
	{
		final String url = "http://vm570.int.janelia.org:8080";
		final Server server = new Server( url );
		final Repository repo = server.createRepo( "test-repo-node", "testing node and dataset creation" );
		final Node root = repo.getRootNode();
		root.createDataset( DatasetBlkLabel.TYPE, DatasetBlkLabel.TYPE  );
		root.createDataset( DatasetBlkRGBA.TYPE, DatasetBlkRGBA.TYPE );
		root.createDataset( DatasetBlkUint8.TYPE, DatasetBlkUint8.TYPE );
		root.createDataset( DatasetKeyValue.TYPE, DatasetKeyValue.TYPE );
		root.createDataset( DatasetLabelVol.TYPE, DatasetLabelVol.TYPE );
		try
		{
			root.createDataset( "there is no", "such data type" );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
	}

}
