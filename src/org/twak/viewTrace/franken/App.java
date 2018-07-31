package org.twak.viewTrace.franken;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;

import org.twak.tweed.TweedSettings;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle;
import org.twak.viewTrace.franken.style.StyleSource;

public abstract class App /*earance*/ implements Cloneable {
	
	public enum TextureUVs {
		Square, Zero_One, Rectangle;
	}
	
	public enum TextureMode {
		Off, Bitmap, Parent, Net, Procedural;

	}
	
	public void install( App child ) {
		
		child.appMode = this.appMode;
		
		switch (child.appMode) {
		case Off:
		default:
			child.styleSource = null;
			child.styleZ = null;
			break;
		case Net:
			if (styleSource instanceof JointStyle)
				child.styleSource = styleSource;
			else
				child.styleSource = new GaussStyle( child.getClass() ); //?! shouldn't we store this somewhere?
			
			child.styleSource.install( child );
			
			break;
		}
	}
	
	public TextureMode appMode = TextureMode.Off;
	
	public double[] styleZ;
	public StyleSource styleSource;
	
	String name;
	
	
	// contains latent variables for children (who are created during tree evluation). 
	public transient Map<Class<? extends App>, double[]> bakeWith = new HashMap<>();
	
	  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		    in.defaultReadObject();
		    this.bakeWith = new HashMap<>();
	  }	
	public App( App a ) {
		this.appMode = a.appMode;
		if (a.styleZ != null )
			this.styleZ = Arrayz.copyOf ( a.styleZ );
		this.name = a.name;
		if (a.styleSource != null)
			this.styleSource = a.styleSource.copy();
	}
	
	public App() {
		
		NetInfo ni = getNetInfo();
		
		this.name = ni.name;
		this.styleZ = new double[ni.sizeZ];
		this.styleSource = new GaussStyle(this.getClass());
	}

	public NetInfo getNetInfo() {
		return NetInfo.index.get(this.getClass());
	}

	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		if (this instanceof FacadeTexApp) 
			out.add (new JLabel (  ((FacadeTexApp)this).coarse));
		
		return out;
	}
	
	static Random randy = new Random();
	static final int Batch_Size = 16;
	
	public static synchronized void computeWithChildren( int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {

		ProgressMonitor pm = TweedSettings.settings.siteplanInteractiveTextures ? null : new ProgressMonitor( null, "Computing...", "...", 0, 100 );

		long startTime = System.currentTimeMillis();

		try {
			computeWithChildren_( Math.max( 1, stage ), todo, globalUpdate, pm );
		} finally {
			if ( pm != null )
				pm.close();
		}

		System.out.println( "time taken " + ( System.currentTimeMillis() - startTime ) );
	}

	private static void computeWithChildren_( int stage, MultiMap<Integer, App> done, Runnable globalUpdate, ProgressMonitor pm ) {

		if ( stage >= NetInfo.index.size() ) {
			return;
		}

		Set<App> all = new LinkedHashSet<>();

		Class k = NetInfo.evaluationOrder.get( stage );

		System.out.println( "computing " + k.getSimpleName() );

		if ( pm != null ) {
			pm.setMaximum( NetInfo.index.size() );
			pm.setProgress( stage );
			pm.setNote( "processing " + k.getSimpleName() );

			if ( pm.isCanceled() )
				return;
		}

		all.addAll( done.get( stage ) );

		// collect all current children from previous stages
		for ( int i = 0; i < stage; i++ )
			for ( App a : done.get( i ) )
				for ( App n : a.getDown( ).valueList() )
					if ( n.getClass() == k ) {

						all.add( n );
					}

		for ( App a : all )
			if ( a.styleSource != null )
				a.styleZ = a.styleSource.draw( randy, a );

		computeBatch( new ArrayList( all ), 0, new Runnable() {
			@Override
			public void run() {
				System.out.println( "finished " + all.size() + " " + k.getSimpleName() + "s" );

				done.putAll( stage, all, true );

				if ( !all.isEmpty() ) 
					all.iterator().next().finishedBatches( new ArrayList<>( all ) );
				
				globalUpdate.run();

				App.computeWithChildren_( stage + 1, done, globalUpdate, pm );
			}

			@Override
			public String toString() {
				return "App.computerWithChildren_";
			}

		}, pm );
	}
	
	private static void computeBatch( List<App> todo, int batchStart, Runnable onDone, ProgressMonitor pm ) {

		if ( batchStart >= todo.size() ) {
			onDone.run();
			return;
		}

		if ( pm != null ) {

			pm.setNote( todo.get( 0 ).name + " " + batchStart + "/" + todo.size() );
			pm.setMaximum( todo.size() );
			pm.setProgress( batchStart );
			if ( pm.isCanceled() )
				return;
		}

		List<App> batch = todo.subList( batchStart, Math.min( todo.size(), batchStart + Batch_Size ) );

		System.out.println( "batch " + batchStart + "/" + todo.size() + " " );

		batch.get( 0 ).computeBatch( () -> App.computeBatch( todo, batchStart + Batch_Size, onDone, pm ), batch );
	}

	public void finishedBatches( List<App> all ) {
		for (App a : all)
			a.markGeometryDirty( );
	}

	public void markGeometryDirty() {
		try {
		App up = getUp();
		if (up != null)
			up.markGeometryDirty();
		}
		catch (NullPointerException e) {
			e.printStackTrace();
		}
	}
	
	public String zAsString() {
		String zs = "";
		for ( double d : styleZ )
			zs += "_" + d;
		return zs;
	}
	
	public abstract App copy();
	public abstract App getUp();
	public abstract MultiMap<String, App> getDown();
	public abstract void computeBatch(Runnable whenDone, List<App> batch);

	public Enum[] getValidAppModes() {
		return new Enum[] {TextureMode.Off, TextureMode.Net};
	}

	public boolean showTextureOptions() {
		return true;
	}

}
