package org.twak.viewTrace.franken;


import java.awt.Color;
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
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle.Joint;
import org.twak.viewTrace.franken.style.StyleSource;

public abstract class App /*earance*/ implements Cloneable {
	
	public enum TextureUVs {
		SQUARE, ZERO_ONE, Rectangle;
	}
	
	public enum AppMode {
		Off, Bitmap, Parent, Net
	}
	
	public AppMode appMode = AppMode.Off;
	public TextureUVs textureUVs = TextureUVs.SQUARE;
	public Color color = Color.gray;
	
	public String texture;
	
	public double[] styleZ;
	public StyleSource styleSource;
	
	public HasApp hasA;
	String name;
	
	// marks as needing geometry recreation
	public boolean isDirty = true; 
	
	// GAN optoins
//	public String netName;
//	public int sizeZ = -1;
//	public int resolution;
	public DRectangle textureRect;
	public Joint lastJoint;
	
	public App( App a ) {
		this.hasA = a.hasA;
		this.appMode = a.appMode;
		this.textureUVs = a.textureUVs;
		this.color = a.color;
		this.texture = a.texture;
		this.styleZ = a.styleZ;
		this.name = a.name;
		this.styleSource = a.styleSource.copy();
	}
	
	public App( HasApp ha ) {
		
		NetInfo ni = NetInfo.index.get(this.getClass());
		
		this.name = ni.name;
		this.hasA = ha; 
		this.styleZ = new double[ni.sizeZ];
		this.styleSource = new GaussStyle(NetInfo.get(this));
	}

	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		if (this.texture != null)
			out.add (new JLabel (this.texture));
		if (this instanceof FacadeTexApp) 
			out.add (new JLabel (  ((FacadeTexApp)this).coarse));
		
		
		return out;
	}

	static Random randy = new Random();
	static final int Batch_Size = 16;
	
	public static synchronized void computeWithChildren (int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {
		
		ProgressMonitor pm =
				TweedSettings.settings.sitePlanInteractiveTextures ? null : 
				new ProgressMonitor( TweedFrame.instance.frame, "Computing...", "...", 0, 100 );
		
		long startTime = System.currentTimeMillis();
		
		try {
			computeWithChildren_( Math.max (1,stage), todo, globalUpdate, pm );
		} finally {
			if (pm != null)
				pm.close();
		}
		
		
		System.out.println("time taken " + (System.currentTimeMillis() - startTime));
		
	}
	
	private static void computeWithChildren_ (int stage, MultiMap<Integer, App> done, Runnable globalUpdate, ProgressMonitor pm ) {

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
				for ( App n : a.getDown().valueList() )
					if ( n.getClass() == k ) { 
						if ( n.appMode == AppMode.Net ) 
							todo.add( n );
						
						all.add(n);
					}
		
		for (App a : todo)
			if ( a.styleSource != null )
				a.styleZ = a.styleSource.draw( randy, a );

		List<App> list = new ArrayList(todo);
		computeBatch ( list , 0, globalUpdate, pm);
		
		System.out.println ("finished "+todo.size()+" " + k.getSimpleName() +"s" );

		done.putAll( stage, all, true );
		
		if (!all.isEmpty()) 
			all.iterator().next().finishedBatches( new ArrayList<>( todo ), new ArrayList<>(all) );
			
		globalUpdate.run();
		
		App.computeWithChildren_( stage + 1, done, globalUpdate, pm );
	}
	private static void computeBatch( List<App> todo, int batchStart, Runnable globalUpdate, ProgressMonitor pm ) {

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
		batch.get( 0 ).computeBatch( () -> App.computeBatch( todo, batchStart + Batch_Size, globalUpdate, pm ), batch );
		
		globalUpdate.run();
	}

//	private static void computeWithChildren_ (int batchStart, int stage, MultiMap<Integer, App> done, 
//			
//			Runnable globalUpdate, Cancellable pm ) {
//		
//		if (pm.cancelled)
//			return;
//		
//		if (stage >= NetInfo.index.size())
//		{
////			globalUpdate.run();
//			return;
//		}
//		
//		
//		if (done.get( stage ).isEmpty()) {
//			App.computeWithChildren_( 0, stage+1, done, globalUpdate, pm );
//			return;
//		}
//		
//		if (batchStart >= done.get(stage).size()) {
//			System.out.println( "finishing "+ done.get( stage ).get( 0 ).getClass().getSimpleName() );
//			
//			done.get(stage).get(0).finishedBatches(done.get(stage));
//			
//			globalUpdate.run();
//			
//			for (App a : new ArrayList<> ( done.get( stage )) ) // app might have created new children
//				for (App next : a.getDown().valueList())
//					done.put( NetInfo.evaluationOrder.indexOf( next.getClass() ), next, true );
//			
//			App.computeWithChildren_( 0, stage+1, done, globalUpdate, pm );
//			
//		} else {
//			
//			List<App> all = done.get( stage );
//			List<App> batch = new ArrayList<>();
//			
//			for ( int i = batchStart; i < Math.min( all.size(), batchStart + Batch_Size ); i++ ) {
//				App app = all.get( i );
//				
//				for (App next : app.getDown().valueList()) // add all children, even if not eval'd
//					done.put( NetInfo.evaluationOrder.indexOf( next.getClass() ), next, true );
//				
//				if (app.appMode == AppMode.Net) {
//					
//					if (app.styleSource != null)
//						app.styleZ = app.styleSource.draw( randy, app );
//					
//					batch.add( app );
//				}
//			}
//			
//			if (!batch.isEmpty()) {
//				System.out.println( "batch " + batchStart +"/"+ all.size() + " "+  done.get( stage ).get( 0 ).getClass().getSimpleName() );
//				batch.get( 0 ).computeBatch ( () -> App.computeWithChildren_( batchStart + Batch_Size, stage, done, globalUpdate, pm ), batch );
////				App.computeWithChildren_( batchStart + Batch_Size, stage, todo, globalUpdate );
//			}
//			else {
//				done.get(stage).get(0).finishedBatches(done.get(stage));
//				globalUpdate.run();
//				App.computeWithChildren_( 0, stage+1, done, globalUpdate, pm );
//			}
//		}
//		
//	}

	public void finishedBatches( List<App> list, List<App> all ) {
		// hook to compute after all batches have run
	}

	public void markDirty() {
		isDirty = true;
		App up = getUp();
		if (up != null)
			up.markDirty();
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
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
	
	
}
