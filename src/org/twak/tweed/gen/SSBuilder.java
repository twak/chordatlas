package org.twak.tweed.gen;

import java.awt.Color;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.ProgressMonitor;

import org.twak.tweed.Tweed;
import org.twak.utils.PaintThing;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.SuperMeshPainter;

public class SSBuilder {

	private ProfileGen profileGen;
	FeatureCache features;
	
	Plot plot;
	
	JSlider megaArea, gisInterior, heightCutoff, mfSoft, pMerge, exposed;
	JCheckBox quick;
	
	public SSBuilder( ProfileGen profileGen, FeatureCache features ) {
		
		this.profileGen = profileGen;
		this.features = features;
		
		megaArea     = new JSlider (0, 1000, 500);
		gisInterior  = new JSlider (0, 1000, 400);
		heightCutoff = new JSlider (0, 1000, 400);
		mfSoft       = new JSlider (0, 1000, 400);
		pMerge       = new JSlider (0, 1000, 800);
		exposed      = new JSlider (0, 1000, 800);
		
		megaArea.addChangeListener( cl -> updateVars() );
		gisInterior.addChangeListener( cl -> updateVars() );
		heightCutoff.addChangeListener( cl -> updateVars() );
		mfSoft.addChangeListener( cl -> updateVars() );
		pMerge.addChangeListener( cl -> updateVars() );
		exposed.addChangeListener( cl -> updateVars() );
		
		JButton preview = new JButton ("preview");
		preview.addActionListener( e -> preview(false) );
		
		JButton save = new JButton ("save");
		save.addActionListener( e -> save( preview( true ) ) );
		
		JButton opt = new JButton ("optimise");
		opt.addActionListener( e -> opt( preview( true ) ) );
		
		quick = new JCheckBox( "quick", true );
		
		PaintThing.debug.clear();
		
		plot = new Plot(preview, save, opt, 
				new JLabel ("mega area"), megaArea, 
				new JLabel("GIS"), gisInterior,
				new JLabel("Height"), heightCutoff,
				new JLabel("MFPoint"), mfSoft,
				new JLabel("ProfMerge"), pMerge,
				new JLabel("Exposed"), exposed,
				quick
				);
	}

	private SkelFootprint updateVars() {
		
		SkelFootprint sf = new SkelFootprint();

		sf.megaFacadeAreaThreshold = megaArea.getValue() * 60 / 1000.;
		SkelFootprint.gisInterior  = gisInterior.getValue() / 1000.;
		sf.heightCutoff            = heightCutoff.getValue() * 10 / 1000.;
		FeatureCache.MFWidthTol      = mfSoft.getValue()  * 10 / 1000.;
		sf.profMergeTol            = pMerge.getValue() / 1000.;
		sf.exitA = quick.isSelected();
		SkelFootprint.exposedFaceFrac = exposed.getValue() / 1000.;
		
		System.out.println("megafacade area threshold " + sf.megaFacadeAreaThreshold );
		System.out.println("gis inters (higher more outside)" + SkelFootprint.gisInterior);
		System.out.println("height insdie/outside threshold " + sf.heightCutoff);
		System.out.println("MFPoint width invalidity " + FeatureCache.MFWidthTol);
		System.out.println("profile over edge merge (higher, less merging)" + sf.profMergeTol);
		System.out.println("exposed face kiler (higher, less removed)" + SkelFootprint.exposedFaceFrac);
		
		return sf;
	}

	
	private void save(SolverState solverState) {
		
		solverState.copy( true ).save( new File ( Tweed.DATA+ File.separator+"blockSolver" + File.separator
		+ profileGen.blockGen.nameCoords() + File.separator +"problem.xml" ), true );
		
		System.out.println("...saved!");
	}
	
	private void opt (SolverState solverState) {
		
		new Thread() {
			@Override
			public void run() {
				
				SkelFootprint.solve( solverState, new ProgressMonitor( null, "optimising", "result.getName()", 0, 100 ), 
						new File (Tweed.SCRATCH+"solver_state.xml"), Long.MAX_VALUE );

				profileGen.tweed.frame.removeGens( SkelGen.class );
				profileGen.tweed.frame.addGen( new SkelGen( solverState.mesh, profileGen.tweed, profileGen.blockGen ), true );
			}
		}.start();
	}

	private SolverState preview(boolean finall) {
		
		PaintThing.lookup.put( HalfMesh2.class, new SuperMeshPainter() );
		PaintThing.debug.clear();
		plot.toPaint.clear();

		SkelFootprint sf = updateVars();
		if (finall) 
			sf.exitA = false;
		
		ProgressMonitor pm = new ProgressMonitor( null, "working","...", 0, 100 );
		
		SolverState SS;
		try {
			SS = sf.buildFootprint( 
				profileGen.footprint, pm, features, profileGen.blockGen );
		}
		finally {
			pm.close();
		}

		if (SS != null) {
			plot.toPaint.add( profileGen.blockGen.nameCoords() );
			plot.toPaint.add( SS.mesh );
			plot.toPaint.add( SS.miniPainter() );
		}
		else
			PaintThing.debug(Color.red, 1, "Bad state");
	
		plot.repaint();
		
		return SS;
	}

}
