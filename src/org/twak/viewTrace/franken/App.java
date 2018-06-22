package org.twak.viewTrace.franken;


import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;

import org.twak.tweed.TweedFrame;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle.Joint;
import org.twak.viewTrace.franken.style.StyleSource;

public abstract class App /*earance*/ implements Cloneable {
	
	public enum TextureUVs {
		Square, Zero_One, Rectangle;
	}
	
	public enum AppMode {
		Off, Bitmap, Parent, Net, Procedural
	}
	
	public AppMode appMode = AppMode.Off;
	
	public double[] styleZ;
	public StyleSource styleSource;
	
	String name;
	
	// marks as needing geometry recreation
	public boolean isDirty = true; 
	
	public Joint lastJoint;
	
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
		this.styleSource = new GaussStyle(NetInfo.get(this));
	}

	public NetInfo getNetInfo() {
		return NetInfo.index.get(this.getClass());
	}

	public JComponent createNetUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		if (this instanceof FacadeTexApp) 
			out.add (new JLabel (  ((FacadeTexApp)this).coarse));
		
		return out;
	}
	
	static Random randy = new Random();
	static final int Batch_Size = 16;
	
	public static synchronized void computeWithChildren ( AppStore ass, int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {
		
		ProgressMonitor pm =
				TweedSettings.settings.sitePlanInteractiveTextures ? null : 
				new ProgressMonitor( TweedFrame.instance.frame, "Computing...", "...", 0, 100 );
		
		long startTime = System.currentTimeMillis();
		
		try {
			computeWithChildren_( ass, Math.max (1,stage), todo, globalUpdate, pm );
		} finally {
			if (pm != null)
				pm.close();
		}
		
		
		System.out.println("time taken " + (System.currentTimeMillis() - startTime));
		
	}
	
	private static void computeWithChildren_ ( AppStore ass, int stage, MultiMap<Integer, App> done, Runnable globalUpdate, ProgressMonitor pm ) {

		if ( stage >= NetInfo.index.size() ) {
			return;
		}

		
		Set<App> todo = new LinkedHashSet<>(), all = new LinkedHashSet<>();

		Class k = NetInfo.evaluationOrder.get( stage );

		System.out.println ("computing " + k.getSimpleName() );
		
		if ( pm != null ) {
			pm.setMaximum( NetInfo.index.size() );
			pm.setProgress( stage );
			pm.setNote( "processing " + k.getSimpleName() );

			if ( pm.isCanceled() )
				return;
		}
		
		todo.addAll( done.get( stage ) ); 
		
		// collect all current children from preivous stages
		for ( int i = 0; i < stage; i++ )
			for ( App a : done.get( i ) ) 
				for ( App n : a.getDown(ass).valueList() )
					if ( n.getClass() == k ) { 
						if ( n.appMode == AppMode.Net ) 
							todo.add( n );
						
						all.add(n);
					}
		
		for (App a : todo)
			if ( a.styleSource != null )
				a.styleZ = a.styleSource.draw( randy, a, ass );

		List<App> list = new ArrayList(todo);
		computeBatch ( list, ass, 0, globalUpdate, pm);
		
		System.out.println ("finished "+todo.size()+" " + k.getSimpleName() +"s" );

		done.putAll( stage, all, true );
		
		if (!all.isEmpty()) 
			all.iterator().next().finishedBatches( new ArrayList<>( todo ), new ArrayList<>(all), ass );
			
		globalUpdate.run();
		
		App.computeWithChildren_( ass, stage + 1, done, globalUpdate, pm );
	}
	private static void computeBatch( List<App> todo, AppStore ass, int batchStart, Runnable globalUpdate, ProgressMonitor pm ) {

		if (batchStart >= todo.size())
			return;
		
		if ( pm != null ) {

			pm.setNote(  todo.get( 0 ).name + " " + batchStart + "/"+todo.size() );
			pm.setMaximum( todo.size() );
			pm.setProgress( batchStart );
			if ( pm.isCanceled() )
				return;
		}
		
		List<App> batch = todo.subList( batchStart, Math.min( todo.size(), batchStart + Batch_Size ) );
		
		System.out.println( "batch " + batchStart + "/" + todo.size() + " " );
		batch.get( 0 ).computeBatch( () -> 
			App.computeBatch( todo, ass, batchStart + Batch_Size, globalUpdate, pm ), batch, ass );
		
		globalUpdate.run();
	}

	public void finishedBatches( List<App> list, List<App> all, AppStore ac ) {
		// hook to compute after all batches have run
	}

	public void markDirty(AppStore ac) {
		isDirty = true;
		App up = getUp(ac);
		if (up != null)
			up.markDirty(ac);
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
		return new Enum[] {AppMode.Off, AppMode.Net};
	}

}
