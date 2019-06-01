package org.twak.tweed.gen.skel;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.twak.viewTrace.franken.style.ui.JointUI;

/**
 * Super hacky code on top of ObjSkelGen to make a list of ObjSkelGen...
 */
public class ObjSkelGenList extends Gen implements IDumpObjs, ICanSave {
	
	transient ObjDump obj; 
	protected String root;
	public File indexFile;
	public Map<String,ObjSkelGen> objSkelGenMap = new HashMap<String,ObjSkelGen>();

	SelectedApps exemplar;

	static final String GENERATED = "done.list";
	
	public ObjSkelGenList(Tweed tweed, String name, String root, File indexFile) {
		super("skel: " + name,  tweed );
		this.root = root;
		this.indexFile = indexFile;

		try {
			List<String> fileList = Files.lines( indexFile.toPath() ).collect( Collectors.toList() );

			for (String file : fileList) {
				// if there's a new line in our todo.list, this forms a file with String ""
				if (file.length() == 0) continue;

				// file looks something like abcd.obj
				String relativePath = root + File.separator + file;
				ObjSkelGen obj = new ObjSkelGen(tweed, "objskel: "+name , relativePath);

				String objName = file.substring(0, file.length() - 4);
				objSkelGenMap.put(objName, obj);

				System.out.println("adding obj skeleton: " + objName);
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}

		setRender();
	}

	protected void setRender() {
		for (ObjSkelGen o : objSkelGenMap.values()) {
			HalfMesh2 hm = new HalfMesh2();
			hm.faces.add( new SuperFace() );
			o.setRender(hm);
		}
	}

	@Override
	public void onLoad( Tweed tweed ) {
		for (ObjSkelGen o : objSkelGenMap.values()) {
			o.onLoad(tweed);
		}
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		for (ObjSkelGen o : objSkelGenMap.values()) {
			o.setVisible(visible);
		}
	}

	/*
		this sets some part of the objSkelGenMap to be visible.
		this is just something so that we only output textured buildings.
		if some part of this objSkelGenMap is visible, the entire thing is marked as visible
	 */
	public void setPartVisible(String filename, boolean visible) {
		// we first set the specified index to be visible
		objSkelGenMap.get(filename).setVisible(visible);

		// if we are setting some subpart to be visible, we want the entire thing
		// to be visible
		if (visible) {
			super.setVisible(visible);
		} else {
			// otherwise, we check to see if everything is not visible.
			// if that's the case, we mark the entire thing as not visible
			boolean someVisible = false;
			for (ObjSkelGen o : objSkelGenMap.values()) {
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
		for (ObjSkelGen o : objSkelGenMap.values()) {
			o.calculate();
		}
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		for (ObjSkelGen o : objSkelGenMap.values()) {
			if (((Gen)o).visible) {
				Jme3z.dump( dump, o.gNode, 0 );
			}
		}
	}

	/*
		Gets the ith update runnable for each building
	*/
	private Runnable getGlobalUpdate(String fileName) {
		final ObjSkelGen o = objSkelGenMap.get(fileName);

		Runnable update = new Runnable() {
			String buildingString = fileName;
			int count = 0;

			@Override
			public void run() {

				tweed.enqueue( new Runnable () {
					@Override
					public void run() {
						o.setSkel( null, null );

						System.out.println("[" + java.time.LocalDate.now() + " " + java.time.LocalTime.now() + "], building " + buildingString + ", version " + count);

						// 11 is output
						// 7 has label information
						if (count == 11 || count == 7) {
							String version = String.format("%02d", count);

							File f = new File (Tweed.FRANKEN_OUTPUT + File.separator + buildingString, "out.obj");
							File f_label = new File (Tweed.FRANKEN_OUTPUT + File.separator + buildingString + "_labels", "labels.obj");

							if (count == 11) {
								ObjDump dump_all = new ObjDump();
								dump_all.REMOVE_DUPE_TEXTURES = true;

								// we dump just this building to the "building" folder
								ObjDump dump = new ObjDump();
								dump.REMOVE_DUPE_TEXTURES = true;
								( (IDumpObjs) o ).dumpObj( dump );
								dump.dump( f, new File ( Tweed.DATA ) );

								try {
									// write to "done.list" that we are done
									File doneFile = new File(Tweed.toWorkspace( root ), GENERATED);
									FileWriter fileWriter;
									if (doneFile.exists()) {
										fileWriter = new FileWriter(doneFile,true);
									} else {
										fileWriter = new FileWriter(doneFile);
									}

									// add the fact that we just wrote something
									fileWriter.write(buildingString + ".obj\n");
									fileWriter.close();

									// remove from "todo.list"
									File old_generate = indexFile;
									File new_generate = new File(Tweed.toWorkspace( root ), "tmp.list");

									BufferedReader reader = new BufferedReader(new FileReader(old_generate));
									BufferedWriter writer = new BufferedWriter(new FileWriter(new_generate));

									String currentLine;

									while((currentLine = reader.readLine()) != null) {
										// trim newline when comparing with lineToRemove
										String trimmedLine = currentLine.trim();
										if(trimmedLine.equals(buildingString + ".obj")) continue;
										writer.write(currentLine + System.getProperty("line.separator"));
									}
									writer.close();
									reader.close();
									new_generate.renameTo(old_generate);

								} catch ( IOException e ) {
									e.printStackTrace();
								}


							} else {
								// this is the 7th iteration
								// we dump to a label thing
								ObjDump dump = new ObjDump();
								dump.REMOVE_DUPE_TEXTURES = true;

								( (IDumpObjs) o ).dumpObj( dump );
								dump.dump( f_label, new File ( Tweed.DATA ) );
							}
						}

						count += 1;
					}
				});
			}

			@Override
			public String toString() {
				return "SkelGen.textureSelectedWithSave";
			}

		};
		return update;
	}

	private void generateStyles(boolean superRes) {
		JointStyle exemplarJS = (JointStyle) exemplar.findRoots().iterator().next().styleSource;

		for (Map.Entry<String, ObjSkelGen> entry : objSkelGenMap.entrySet()) {
			String filename = entry.getKey();
			ObjSkelGen o = entry.getValue();
			Runnable update = getGlobalUpdate(filename);

			SelectedApps sa = new SelectedApps( o.blockApp , update);
			BlockApp b =  (BlockApp) sa.findRoots().iterator().next();

			// same as clicking "joint"
			// working functionality, commenting out to figure out UI
			if (superRes) {
				// same as clicking "high"
				b.setJoint(sa, exemplarJS, true);
			} else {
				b.setJoint(sa, exemplarJS, false);
			}

			// same as clicking "redraw distribution"
			System.out.println("waiting for " + filename + " to generate textures...");
			sa.markDirty();
			sa.computeTextures(null);
			System.out.println(filename + " completed!");
			
		}
	}

	@Override
	public JComponent getUI() {
		JPanel ui = new JPanel( new ListDownLayout() );

		// new JointUI( sa, update ) takes in sa and update, but it seems that
		// update isn't used, and the only purpose for sa is for the joint
		JButton edit = new JButton( "edit joint (all)" );
		String filename0 = objSkelGenMap.keySet().iterator().next();
		Runnable update0 = getGlobalUpdate(filename0);
		exemplar = new SelectedApps( objSkelGenMap.get(filename0).blockApp , update0);
		BlockApp b =  (BlockApp) exemplar.findRoots().iterator().next();
		b.setJoint(exemplar);
		edit.addActionListener( e -> new JointUI( exemplar, null ).openFrame() );
		ui.add( edit );

		JButton medium = new JButton( "generate styles (medium)" );
		medium.addActionListener( l -> generateStyles(false) );
		ui.add( medium );

		JButton styleSuper = new JButton( "generate styles (super)" );
		styleSuper.addActionListener( l -> generateStyles(true) );
		ui.add( styleSuper );
		
		return ui;
	}
	
}
