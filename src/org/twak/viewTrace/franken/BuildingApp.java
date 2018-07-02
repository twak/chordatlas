package org.twak.viewTrace.franken;

import java.util.List;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.tweed.gen.skel.SiteplanDesigner;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.style.JointStyle.Joint;

public class BuildingApp extends App {

	public SkelGen parent;
	public double probDormer = 0.5;
	public boolean createDormers;
	public String chimneyTexture;
	public SuperFace superFace;
	public boolean isGeometryDirty;
	
	// if a jointStyle is in use, the joint assigned to this hierarchy
	public Joint lastJoint;
	
	public BuildingApp( SuperFace superFace ) {
		super( );
		this.superFace = superFace;
	}

	public BuildingApp( BuildingApp buildingApp ) {
		super (buildingApp);
		this.parent = buildingApp.parent;
		this.superFace = buildingApp.superFace;
	}

	@Override
	public App copy() {
		return new BuildingApp( this );
	}

	@Override
	public App getUp(AppStore ac) {
		return ac.get ( BlockApp.class, parent );
	}

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		
		MultiMap<String, App> down = new MultiMap<>();
		
		down.put( "roof",  ac.get( RoofGreebleApp.class, superFace.mr ) ); 
		
		for (HalfEdge e : superFace) 
			down.put ( "facade", ac.get(FacadeLabelApp.class, ((SuperEdge)e).toEdit ) );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch, AppStore appCache ) {
		whenDone.run();
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		addRemoveButton (globalUpdate, out);
		
		out.add(new AutoDoubleSlider( this, "probDormer", "p (dormer)", 0,1 ) {
			public void updated(double value) {
				updateDormers( new Random() );
				globalUpdate.run();
			};
		} );
		
		JButton siteplan = new JButton( "edit plan/profile" );
		siteplan.addActionListener( e -> new SiteplanDesigner( superFace, parent ) );
		out.add(siteplan);
		
		return out;
	}
	
	private void addRemoveButton( Runnable globalUpdate, JPanel out ) {
		JButton remove = new JButton( "remove building" );
		remove.addActionListener( e -> {
			parent.block.faces.remove( superFace );
			parent.calculateOnJmeThread();
		} );
		out.add( remove );
	}

	public void updateDormers(Random randy) {
		 createDormers = randy.nextDouble() < probDormer;
	}
	
	@Override
	public void markGeometryDirty( AppStore ac ) {
		this.isGeometryDirty = true;
		super.markGeometryDirty( ac );
	}
}
