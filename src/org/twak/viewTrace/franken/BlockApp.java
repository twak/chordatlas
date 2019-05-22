package org.twak.viewTrace.franken;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle;

public class BlockApp extends App {

	public static final int SKIRT_RES = 2048;

	public SkelGen skelGen;

	public boolean doSkirt = false;
	private DRectangle skirt;
	public String skirtTexture;
	
	public BlockApp( BlockApp buildingApp ) {
		super (buildingApp);
		this.skelGen = buildingApp.skelGen;
		
		setSkirt( skelGen.block.getBounds() ); 
	}

	public BlockApp( SkelGen skelGen ) {
		super ( );
		this.skelGen = skelGen;
	}

	@Override
	public App copy() {
		return new BlockApp( this );
	}

	@Override
	public App getUp() {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> down = new MultiMap<>();

		for (HalfFace sf : skelGen.block) 
			down.put( "building", ((SuperFace) sf).buildingApp );
		
		return down;
	}

	
	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		
		skirtTexture = null;
		
		whenDone.run();
	}

	public void setIndp( SelectedApps sa ) {
		setGauss( Collections.singletonList( this ) );
		sa.showUI();
	}

	private void setGauss( List<App> hashSet ) {
		for (App a : hashSet) { 
			a.styleSource = new GaussStyle( a.getClass() );
			a.appMode = TextureMode.Net;
			setGauss (a.getDown( ).valueList());
		}
	}

	public void setJoint( SelectedApps sa, JointStyle js, boolean superRes) {
		styleSource = js;
		appMode = TextureMode.Net;
		styleSource.install( new SelectedApps( (App) this, sa.geometryUpdate ) );
		sa.showUI();

		if (superRes) {
			js.setSuper();
		}
		js.redraw();
	}

	public void setJoint( SelectedApps sa ) {
		styleSource = new JointStyle(null);
		appMode = TextureMode.Net;
		styleSource.install( new SelectedApps( (App) this, sa.geometryUpdate ) );
		sa.showUI();
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps sa ) {
		
		JPanel out = new JPanel(new ListDownLayout());

		
		if (styleSource instanceof JointStyle) {
			JButton g = new JButton("set independent");
			
			g.addActionListener( e -> setIndp( sa ) );
			
			out.add(g);
			
			out.add ( new AutoCheckbox( this, "doSkirt", "create skirt" ) {
				public void updated(boolean selected) {
					for (App a : sa) 
						((BlockApp)a).doSkirt = selected;
					globalUpdate.run();
				};
			} );
			
		}
		else {
			
			JButton j = new JButton("set joint");
			
			j.addActionListener( new ActionListener() {
				
				@Override
				public void actionPerformed( ActionEvent e ) {
					setJoint( sa );
				}

			} );
			
			out.add(j);
		}
		
		return out;
	}

	public DRectangle getSkirt() {
		
		if (skirt == null)
			skirt = skelGen.block.getBounds().grow( 10 ); 
		
		return skirt;
	}

	public void setSkirt( DRectangle skirt ) {
		this.skirt = skirt;
	}
	
	public boolean showTextureOptions() {
		return false;
	}
}
