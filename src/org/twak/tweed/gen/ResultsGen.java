package org.twak.tweed.gen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.GenHandlesSelect;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.utils.HalfMesh2;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.utils.HalfMesh2.HalfFace;
import org.twak.utils.ListDownLayout;
import org.twak.utils.LoopL;
import org.twak.utils.Loopz;
import org.twak.utils.ObjDump;
import org.twak.utils.Parallel;
import org.twak.viewTrace.facades.Regularizer;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;

public class ResultsGen extends Gen implements IDumpObjs, GenHandlesSelect {

	File root;
	
	List<MeshFile> toAdd = Collections.synchronizedList( new ArrayList<>() );

	Map<File, Node> rendered = new HashMap<>();
	
	int footprints = 0;
	
	public ResultsGen( File file, Tweed tweed ) {
		
		super ("results", tweed);
		this.root = file;
	}

	private void load( File f, boolean plot ) {

		plansIn.set( 0 );
		plansOut.set( 0 );
		meshFacades.set(0);
		
		Regularizer.miniFacadesUsed = 0;
		Regularizer.regularised  = 0; 
		Regularizer.seenImages.clear();
		Regularizer.totalFeature = 0;
		
		try {
					
			List<File> files = Files.walk(f.toPath())
			.filter(Files::isRegularFile)
			.map (p -> p.toFile())
			.filter (s -> s.getName().equals("done.xml" ) )
			.collect( Collectors.toList() );
			
			
//			files = files.subList( 0, Math.min (files.size(), 3) );
			
			new Parallel<File, MeshFile>( files, x -> readMesh(x, plot), meshes -> loaded(meshes), false );
			
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		calculateOnJmeThread();
	}

	
	private void loaded( Set<MeshFile> meshes ) {
		
		toAdd.addAll( meshes );
		
		calculateOnJmeThread();
	}

	static class MeshFile {
		HalfMesh2 mesh;
		File file;
		
		public MeshFile (HalfMesh2 mesh, File file) {
			this.mesh = mesh;
			this.file = file;
		}
	}
	
	AtomicInteger plansOut = new AtomicInteger(), plansIn = new AtomicInteger(), meshFacades = new AtomicInteger();
	
	private MeshFile readMesh(File f, boolean plot) {
		
//		if (f.getName().contains( "482.0861860288687_-965.1306367886863" )) 
//			return null;
		
		System.out.println("reading solution " + f.getParentFile().getName());
		
		try {
			SolverState SS = (SolverState) new XStream().fromXML( f );
			
			for (HalfFace hf : SS.mesh) 
				plansIn.incrementAndGet();
			
			SkelFootprint.postProcesss( SS );
			
			for (HalfFace hf : SS.mesh) { 
				plansOut.incrementAndGet();
				
				for (HalfEdge e : hf) {
					SuperEdge se = (SuperEdge) e;
					if (se.proceduralFacade != null) {
						meshFacades.getAndIncrement();
					}
				}
				
			}
			
			if (plot)
				SS.debugSolverResult();
			
			return new MeshFile ( SS.mesh, f );
		} catch ( Throwable t ) {
			t.printStackTrace();
			return null;
		}
	}
	
	Map<HalfMesh2, Node> nodes = new HashMap<>();
	
	@Override
	public void calculate() {
		
		synchronized ( toAdd ) {

			for ( MeshFile block : toAdd ) {
				if ( block != null ) {
					
					Node n = rendered.get(block.file);
					if (n != null)
						n.removeFromParent();
					
					rendered.remove(block.file);
					
					SkelGen sg = new SkelGen( tweed );

					sg.toRender = block.mesh;
					sg.parentNode = gNode;
					sg.calculate();
					ResultsGen.this.gNode.attachChild( sg.gNode );
					rendered.put( block.file, sg.gNode );
				}
			}

			toAdd.clear();
		}
		
		System.out.println(" before: " + plansIn.get() +" after " + plansOut.get() );
		System.out.println(" buidling facades: " + meshFacades.get() );
		System.out.println(" minifacades read: " + Regularizer.miniFacadesUsed +" fac regularised " +
				Regularizer.regularised + "  total images " + 
				Regularizer.seenImages.size() );
		System.out.println(" total features: " + Regularizer.totalFeature);
			
			
//			if ( block != null )
//				for ( HalfFace f : block ) {
//
//					try {
//					PlanSkeleton skel = SkelGen.calc( (SuperFace) f );
//
//					if ( skel != null ) {
//
//						Node house = new Greeble( tweed ).showSkeleton( skel.output, new OnClick() {
//							@Override
//							public void selected( Output output, Node house2, SuperEdge se ) {
//							}
//						} );
//
//						gNode.attachChild( house );
//					}
//					}
//					catch (Throwable th) {
//						System.out.println( "while processing lbock wiht corner "+block.faces.get( 0 ).e.start );
//						th.printStackTrace();
//					}
//				}

//		if (!pNode.getChildren().isEmpty())
//			tweed.frame.addGen( new JmeGen( "sProfs", tweed, pNode ), false );
		
		gNode.updateModelBound();
		gNode.updateGeometricState();
		
		super.calculate();
	}
	
	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel();
		out.setLayout( new ListDownLayout() );
		
		JButton all = new JButton ("load all");
		all.addActionListener( e -> new Thread ( () -> load(root, false) ).start() );
		out.add(all);
		
		JButton clear = new JButton ("clear");
		clear.addActionListener( e -> clear() );
		out.add(clear);
		
		return out;
	}

	private void clear() {
		toAdd.clear();
		rendered.clear();
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		calculateOnJmeThread();
		
		tweed.enqueue( new Runnable() {
			
			@Override
			public void run() {
				Jme3z.removeAllChildren( gNode );
			}
		} );
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump( dump, gNode, 1 );
	}

	@Override
	public void select( Spatial target, Vector3f contactPoint, Vector2f cursorPosition ) {
//		tweed.
	}

	@Override
	public void blockSelected( LoopL<Point3d> polies, BlockGen blockGen ) {
		
		Point2d cen = Loopz.average( Loopz.to2dLoop( polies, 1, null ) );
		
		for (File f : new File (tweed.DATA+File.separator +"solutions").listFiles()) {
			try {
			Point2d fp = FeatureGen.fileToCenter( f.getName() );
			if (fp.distanceSquared( cen ) < 1) {
				new Thread( () -> load( f, true ) ).start();
				return;
			}
			}
			catch (Throwable th) {
				System.out.println( "unable to read solution "+f.getName() );
			}
		}
		
		JOptionPane.showMessageDialog( tweed.frame, "Can't find solution for center " + cen );
	}

}
