package org.twak.tweed.gen;

import java.io.File;

import javax.swing.ProgressMonitor;

import org.twak.tweed.TweedFrame;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.geom.ObjDump;

import com.thoughtworks.xstream.XStream;

public class SkelFootprintConsole {

	private void go( SolverState fromXML, File result, long runTime ) {
		SkelFootprint.solve( fromXML, new ProgressMonitor( null, "optimising", result.getName(), 0, 100 ), result, runTime );
	}
	
	public static void main (String args[]) {
	
		System.out.println("solving: first arg: input state. second arg: output state" );
		System.out.println("reconstruction: first arg: input state. outputs as obj. can be given a folder of blocks" );
		
		File f = new File (args[0]);
		
		if (!f.exists()) {
			System.out.println(args[0] +" not found");
			return;
		}

		if ( args.length >= 2 ) {
			System.out.println( "running solver" );
			
			long runTime = Long.MAX_VALUE;
			if (args.length >= 3) {
				runTime = Long.parseLong( args[2] );
				System.out.println("for " + runTime +" seconds");
			}
			
			new SkelFootprintConsole().go( (SolverState) new XStream().fromXML( f ), new File( args[ 1 ] ), runTime );
		} else if ( args.length == 1 ) {
			
//			File f = new File (args[0]);
			ObjDump obj = new ObjDump();
			
			TweedFrame.HEADLESS= true;
			TweedFrame tf = new TweedFrame();
			SkelGen gen = new SkelGen(tf.tweed);
			
			File out;
			
			if ( f.exists() && f.isDirectory() ) {
				out = new File( "combined.obj" );
				
				for (File b: f.listFiles())
					dump( gen, new File (b, "done.xml" ) , obj );
				
			} else {
				out = new File( args[ 0 ] + ".obj" );
				dump( gen, new File( args[ 1 ] ), obj );
			}
			
			obj.dump( out );
			
			tf.tweed.destroy();
			System.exit(0);
			
		} else {
			System.out.println( "takes 1 or 2 arguments" );
			return;
		}
		
	}

	private static void dump( SkelGen gen, File f, ObjDump obj ) {
		
		SolverState SS= (SolverState) new XStream().fromXML( f );
		
		SkelFootprint.postProcesss(SS);
		
		gen.toRender = SS.mesh;
		gen.calculate();

		gen.dumpObj( obj );

	}
	
}
