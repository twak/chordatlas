package org.twak.viewTrace.franken;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.gen.skel.AppStore;
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

	SkelGen skelGen;

	public boolean doSkirt = false;
	public DRectangle skirt;
	public String skirtTexture;
	
	public BlockApp( BlockApp buildingApp ) {
		super (buildingApp);
		this.skelGen = buildingApp.skelGen;
		
		skirt = skelGen.block.getBounds(); 
	}

	public BlockApp( SkelGen skelGen ) {
		super ( );
		this.skelGen = skelGen;
		skirt = skelGen.block.getBounds().grow( 10 ); 
	}

	@Override
	public App copy() {
		return new BlockApp( this );
	}

	@Override
	public App getUp(AppStore ac) {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		
		MultiMap<String, App> down = new MultiMap<>();

		for (HalfFace sf : skelGen.block) 
			down.put( "building", ac.get(BuildingApp.class, sf ) );
		
		return down;
	}

	
	@Override
	public void computeBatch( Runnable whenDone, List<App> batch, AppStore ass ) {
		
		skirtTexture = null;
		
		whenDone.run();
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps sa ) {
		
		JPanel out = new JPanel(new ListDownLayout());

		
		if (styleSource instanceof JointStyle) {
			JButton g = new JButton("set normal");
			
			g.addActionListener( new ActionListener() {
				@Override
				public void actionPerformed( ActionEvent e ) {
					setGauss( Collections.singletonList( BlockApp.this ) );
					sa.showUI();
				}

				private void setGauss( List<App> hashSet ) {
					for (App a : hashSet) { 
						a.styleSource = new GaussStyle( BlockApp.this.getClass() );
						a.appMode = AppMode.Net;
						setGauss (a.getDown( sa.ass ).valueList());
					}
				}
			} );
			
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
					styleSource = new JointStyle(null);
					appMode = AppMode.Net;
					styleSource.install( new SelectedApps( (App) BlockApp.this, sa.ass, sa.geometryUpdate ) );
					sa.showUI();
				}
			} );
			
			out.add(j);
		}
		
		return out;
	}
}
