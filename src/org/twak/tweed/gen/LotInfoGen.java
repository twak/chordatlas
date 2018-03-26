package org.twak.tweed.gen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.vecmath.Point3d;

import org.twak.tweed.Tweed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.SuperLoop;

public class LotInfoGen extends Gen implements ICanSave {

	public transient Map<String, OS> heights = new HashMap();

	File location;
	
	public static class OS {
		double abshmin, absh2, abshmax, relh2, relhmax;

		public OS( double abshmin, double absh2, double abshmax, double relh2, double relhmax ) {
			this.abshmin = abshmin;
			this.absh2 = absh2;
			this.abshmax = abshmax;
			this.relh2 = relh2;
			this.relhmax = relhmax;
		}
	}

	public LotInfoGen() {}
	
	public LotInfoGen( File csvfile, Tweed tweed ) {
		super( csvfile.getName(), tweed );
		this.location = csvfile.getParentFile();
		
		refresh();
	}
	
	@Override
	public void onLoad( Tweed tweed ) {
		super.onLoad( tweed );
		refresh();
	}
	
	public void refresh() {

		final int[] count = new int[1];

		
		for ( File csv : Tweed.toWorkspace( location ).listFiles() ) {
			if ( csv.getName().endsWith( ".csv" ) ) {
				System.out.println( "loading to metadata from " + csv.getName() );

				try ( Stream<String> stream = Files.lines( csv.toPath() ) ) {

					stream.forEach( new Consumer<String>() {
						@Override
						public void accept( String line ) {
							String[] vals = line.split( "," );
							String name = vals[ 0 ];

							try {
								heights.put( name, new OS( Double.parseDouble( vals[ 5 ] ), Double.parseDouble( vals[ 6 ] ), Double.parseDouble( vals[ 7 ] ), Double.parseDouble( vals[ 8 ] ), Double.parseDouble( vals[ 9 ] ) ) );
							}
							catch (NumberFormatException e) {}

							count[ 0 ]++;
						}
					} );

				} catch ( IOException e ) {
					e.printStackTrace();
				}

			}
		}
		System.out.println( count[ 0 ] + " records loaded." );
	}
	

	@Override
	public JComponent getUI() {
		return new JLabel("serving data for " + heights.size()+" blocks");
	}

	public Map<String,Object> getProperties( Loop<Point3d> l ) {
		
		SuperLoop<Point3d> sl = (SuperLoop)l;
		
		Map<String, Object> out = new HashMap();
		
		OS os = heights.get(sl.properties.get( "name" ));
		
		if (os != null) {
			out.put( "abshmin", os.abshmin );
			out.put( "absh2", os.absh2 );
			out.put( "abshmax", os.abshmax );
			out.put( "relh2", os.relh2 );
			out.put( "relhmax", os.relhmax );
		}
		
		return out;
	}

}
