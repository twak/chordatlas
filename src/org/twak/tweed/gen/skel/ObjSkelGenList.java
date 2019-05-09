package org.twak.tweed.gen.skel;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Output;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.PlanSkeleton.ColumnProperties;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.ICanSave;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.skel.ObjSkelGen;
import org.twak.tweed.gen.SkelFootprint;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.IDumpObjs;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjDump.Face;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.JointStyle;

/**
 * Super hacky code on top of ObjSkelGen to make a list of ObjSkelGen...
 */
public class ObjSkelGenList extends Gen implements IDumpObjs, ICanSave {
	
	transient ObjDump obj; 
	String root;
	public List<String> fileNameList = new ArrayList<String>();
	public List<ObjSkelGen> objSkelGenList = new ArrayList<ObjSkelGen>();
	
	public ObjSkelGenList(Tweed tweed, String name, String root ) {
		super("skel: " + name,  tweed );
		this.root = root;

		String[] fileList = Tweed.toWorkspace( root ).list();
 
        for (String file : fileList) {
        	String filename = root + File.separator + file;
            ObjSkelGen obj = new ObjSkelGen(tweed, "objskel: "+name , filename);
            objSkelGenList.add(obj);
            fileNameList.add(filename);

            System.out.println("adding obj skeleton: " + filename);
        }

		setRender();
	}

	protected void setRender() {
		for (ObjSkelGen o : objSkelGenList) {
			HalfMesh2 hm = new HalfMesh2();
			hm.faces.add( new SuperFace() );
			o.setRender(hm);
		}
	}

	@Override
	public void onLoad( Tweed tweed ) {
		for (ObjSkelGen o : objSkelGenList) {
			o.onLoad(tweed);
		}
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		for (ObjSkelGen o : objSkelGenList) {
			o.setVisible(visible);
		}
	}

	/*
        this sets some part of the objskelgenlist to be visible. 
        this is just something so that we only output textured buildings.
	    if some part of this objskelgenlist is visible, the entire thing is marked as visible
	 */
	public void setPartVisible(int objInd, boolean visible) {
		// we first set the specified index to be visible
		objSkelGenList.get(objInd).setVisible(visible);

		// if we are setting some subpart to be visible, we want the entire thing
		// to be visible
		if (visible) {
			super.setVisible(visible);
		} else {
			// otherwise, we check to see if everything is not visible.
			// if that's the case, we mark the entire thing as not visible
			boolean someVisible = false;
			for (ObjSkelGen o : objSkelGenList) {
				if (((Gen)o).visible) {
					someVisible = true;
				}
			}
			if (!someVisible) {
				super.setVisible(visible);
			}
		}
	}

	@Override
	public void calculate() {
		for (ObjSkelGen o : objSkelGenList) {
			o.calculate();
		}
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		for (ObjSkelGen o : objSkelGenList) {
			Jme3z.dump( dump, o.gNode, 0 );
		}
	}

	@Override
	public JComponent getUI() {
		JPanel ui = new JPanel( new ListDownLayout() );
		
		JButton auto = new JButton( "edit material w/ autosave" );
		auto.addActionListener( l -> {
			for (int i = 0; i < objSkelGenList.size(); i++) {
				final int buildingN = i;
				final ObjSkelGen o = objSkelGenList.get(buildingN);

				Runnable update = new Runnable() {
					int buildingNum = buildingN;
					int count = 0;

					@Override
					public void run() {

						tweed.enqueue( new Runnable() {

							@Override
							public void run() {
								setPartVisible(buildingNum, true);
								o.setSkel( null, null );

								System.out.println("[" + java.time.LocalTime.now() + "], backup version " + count + ", building# " + buildingNum);
								String version = String.format("%02d", count);
								String buildingCount = String.format("%03d", buildingNum);

								File f = new File (Tweed.BACKUP + File.separator + buildingCount + "_" + version, "out.obj");
								File f_final = new File (Tweed.BACKUP + File.separator + "_final", "out.obj");

								ObjDump dump = new ObjDump();
								dump.REMOVE_DUPE_TEXTURES = true;

								for ( Gen g : tweed.frame.genList )
									if ( g.visible && g instanceof IDumpObjs )
										( (IDumpObjs) g ).dumpObj( dump );

								//( (IDumpObjs) o ).dumpObj( dump );
								
								dump.dump( f, new File ( Tweed.DATA ) );
								dump.dump( f_final, new File ( Tweed.DATA ) );
								count += 1;
							}
						} );
					}

					@Override
					public String toString() {
						return "SkelGen.textureSelectedJoan";
					}

				};
				SelectedApps sa = new SelectedApps( o.blockApp , update);
				BlockApp b =  (BlockApp) sa.findRoots().iterator().next();

				// same as clicking "joint"
				JointStyle js = b.setAndGetJoint(sa);

				// same as clicking "high"
				js.setSuper();

				// same as clicking "redraw distribution"
				sa.markDirty();
				sa.computeTexturesNewThread();
			}

			setVisible(false);
			
		} );
		ui.add( auto );
		
		return ui;
	}
	
}
