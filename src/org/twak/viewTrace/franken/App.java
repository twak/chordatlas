package org.twak.viewTrace.franken;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
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
		return new JPanel();
	}

	static Random randy = new Random();
	static final int Batch_Size = 16;
	
	public static synchronized void computeWithChildren (int batchStart, int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {
		
		computeWithChildren_( batchStart, stage, todo, globalUpdate );
	}
	
	private static void computeWithChildren_ (int batchStart, int stage, MultiMap<Integer, App> todo, Runnable globalUpdate ) {
		
		if (stage >= NetInfo.index.size())
		{
//			globalUpdate.run();
			return;
		}
		
		if (todo.get( stage ).isEmpty()) {
			App.computeWithChildren_( 0, stage+1, todo, globalUpdate );
			return;
		}
		
		if (batchStart >= todo.get(stage).size()) {
			System.out.println( "finishing "+ todo.get( stage ).get( 0 ).getClass().getSimpleName() );
			
			todo.get(stage).get(0).finishedBatches(todo.get(stage));
			
			globalUpdate.run();
			
			for (App a : new ArrayList<> ( todo.get( stage )) ) // app might have created new children
				for (App next : a.getDown().valueList())
					todo.put( NetInfo.evaluationOrder.indexOf( next.getClass() ), next, true );
			
			App.computeWithChildren_( 0, stage+1, todo, globalUpdate );
			
		} else {
		
			List<App> all = todo.get( stage );
			List<App> batch = new ArrayList<>();
			
			for ( int i = batchStart; i < Math.min( all.size(), batchStart + Batch_Size ); i++ ) {
				App app = all.get( i );
				
				for (App next : app.getDown().valueList()) // add all children, even if not eval'd
					todo.put( NetInfo.evaluationOrder.indexOf( next.getClass() ), next, true );
				
				if (app.appMode == AppMode.Net) {
					
					if (app.styleSource != null)
						app.styleZ = app.styleSource.draw( randy, app );
					
					batch.add( app );
				}
			}

			if (!batch.isEmpty()) {
				System.out.println( "batch " + batchStart +"/"+ all.size() + " "+  todo.get( stage ).get( 0 ).getClass().getSimpleName() );
				batch.get( 0 ).computeBatch ( () -> App.computeWithChildren_( batchStart + Batch_Size, stage, todo, globalUpdate ), batch );
//				App.computeWithChildren_( batchStart + Batch_Size, stage, todo, globalUpdate );
			}
			else {
				globalUpdate.run();
				App.computeWithChildren_( 0, stage+1, todo, globalUpdate );
			}
		}
		
	}

	public void finishedBatches( List<App> list ) {
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
