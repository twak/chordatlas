package org.twak.viewTrace.franken;


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
import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle.Joint;
import org.twak.viewTrace.franken.style.StyleSource;

public abstract class App /*earance*/ implements Cloneable {
	
	public enum TextureUVs {
		Square, Zero_One, Rectangle;
	}
	
	public enum AppMode {
		Manual, Bitmap, Parent, Net, Procedural
	}
	
	public AppMode appMode = AppMode.Manual;
	
	public double[] styleZ;
	public StyleSource styleSource;
	
	String name;
	
	
	// contains latent variables for children (who are created during tree evluation). 
	public transient Map<Class<? extends App>, double[]> bakeWith = new HashMap<>();
	
	public App( App a ) {
		this.appMode = a.appMode;
		this.styleZ = a.styleZ;
		this.name = a.name;
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
	
	static boolean alreadyIn = false;

	public static synchronized void computeWithChildren( AppStore ass, int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {

		if ( alreadyIn ) {
			throw new Error();
		}

		try {
			alreadyIn = true;

			ProgressMonitor pm = TweedSettings.settings.sitePlanInteractiveTextures ? null : new ProgressMonitor( null, "Computing...", "...", 0, 100 );

			long startTime = System.currentTimeMillis();

			try {
				computeWithChildren_( ass, Math.max( 1, stage ), todo, globalUpdate, pm );
			} finally {
				if ( pm != null )
					pm.close();
			}

			System.out.println( "time taken " + ( System.currentTimeMillis() - startTime ) );
		} finally {
			alreadyIn = false;
		}
	}

	private static void computeWithChildren_( AppStore ass, int stage, MultiMap<Integer, App> done, Runnable globalUpdate, ProgressMonitor pm ) {

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
				for ( App n : a.getDown( ass ).valueList() )
					if ( n.getClass() == k ) {

						all.add( n );
					}

		for ( App a : all )
			if ( a.styleSource != null )
				a.styleZ = a.styleSource.draw( randy, a, ass );

		computeBatch( new ArrayList( all ), ass, 0, new Runnable() {
			@Override
			public void run() {
				System.out.println( "finished " + all.size() + " " + k.getSimpleName() + "s" );

				done.putAll( stage, all, true );

				if ( !all.isEmpty() ) 
					all.iterator().next().finishedBatches( new ArrayList<>( all ), ass );
				
				globalUpdate.run();

				App.computeWithChildren_( ass, stage + 1, done, globalUpdate, pm );
			}

			@Override
			public String toString() {
				return "App.computerWithChildren_";
			}

		}, pm );
	}
	
	private static void computeBatch( List<App> todo, AppStore ass, int batchStart, Runnable onDone, ProgressMonitor pm ) {

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

		batch.get( 0 ).computeBatch( () -> App.computeBatch( todo, ass, batchStart + Batch_Size, onDone, pm ), batch, ass );
	}

	public void finishedBatches( List<App> all, AppStore ass ) {
		for (App a : all)
			a.markGeometryDirty( ass );
	}

	public void markGeometryDirty(AppStore ac) {
		App up = getUp(ac);
		if (up != null)
			up.markGeometryDirty(ac);
	}
	
	public String zAsString() {
		String zs = "";
		for ( double d : styleZ )
			zs += "_" + d;
		return zs;
	}
	
	public abstract App copy();
	public abstract App getUp(AppStore ass);
	public abstract MultiMap<String, App> getDown(AppStore ass);
	public abstract void computeBatch(Runnable whenDone, List<App> batch, AppStore ass);

	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Manual, AppMode.Net};
	}

}
