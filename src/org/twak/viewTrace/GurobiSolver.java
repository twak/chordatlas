package org.twak.viewTrace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.vecmath.Point2d;

import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.ObjDump;

import gurobi.GRB;
import gurobi.GRB.DoubleAttr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBQuadExpr;
import gurobi.GRBVar;

public class GurobiSolver {

	private static class State {
		
		LineSoup filtered;
		FindLines lines;
		Concarnie con;
		double height;
		
		public State( LineSoup filtered, FindLines lines, Concarnie con, double height ) {
			this.filtered = filtered;
			this.lines = lines;
			this.con = con;
			this.height = height;
		}
	}
	
	File outfile;
	SliceParameters P;
	
	public GurobiSolver(File outFile, Slice slice, SliceParameters P) {
		this.outfile = outFile;
		this.P  = P;
		go(slice);
	}
	
	private void go( Slice slice ) {

		double delta = 4;

		List<State> states = new ArrayList<>();

		for ( double d = slice.min[ slice.majorAxis ]; d <= slice.max[ slice.majorAxis ]; d += delta ) {
			System.out.println( "processing slice " + d + " / " + slice.max[ slice.majorAxis ] );

			boolean error;
			State s = null;
			int count = 0;
			do {
				error = false;
				try {

					LineSoup rawSoup = slice.sliceMesh( slice.rawMesh, d );
					LineSoup s2 = slice.sliceMesh( slice.filteredMesh, d );
					FindLines foundLines = new FindLines( s2, slice.gisBias, -1, rawSoup.clique(P.CL_HIGH, P.CL_LOW), P );
					Concarnie c = new Concarnie( foundLines.result, foundLines.cliques, P );
					s = new State( s2, foundLines, c, d );
				} catch ( Throwable th ) {
					error = true;
				}
			} while ( error && count++ < 10 );

//			if (!s.con.out.isEmpty())
			states.add( s );
		}

//		slicesByDataFit( slice, delta, states );

		buildRunGurobi( slice, delta, states );

	}

	private void buildRunGurobi( Slice slice, double delta, List<State> states ) {

		double[][] fit = new double[states.size()][states.size()];

		System.out.println( "data fit" );
		for ( int i = 0; i < states.size(); i++ ) {
			for ( int j = 0; j < states.size(); j++ ) {
				fit[ i ][ j ] = dataFit( states.get( i ).filtered, states.get( j ).con.out );
				System.out.print( fit[ i ][ j ] + " " );
			}
			System.out.println();
		}

		System.out.println( "slice align" );
		double[][] align = new double[states.size()][states.size()];

		for ( int i = 0; i < states.size(); i++ ) {
			for ( int j = 0; j < states.size(); j++ ) {
				align[ j ][ i ] = align( states.get( i ).con.out, states.get( j ).con.out );
				System.out.print( align[ j ][ i ] + " " );
			}
			System.out.println();
		}
		
//		for ( int j = 0; j < states.size(); j++ ) {
//			double t = 0;
//			for ( int i = 0; i < states.size(); i++ ) {
//				if (fit[i][j] != Double.MAX_VALUE)
//					t += fit[i][j];
//			}
//			System.out.println(" j " +j +" ++ "+t);
//		}

		int[] res = sliceOptimize( states.size(), fit, align );

		if (res == null) {
			System.err.println("no solution found");
			return;
		}
		
		ObjDump out = new ObjDump();
		
		states.stream().forEach( x -> CutHoles.cutHoles(x.con.out, P.CON_TOL/5) );
		
		double height = slice.min[ slice.majorAxis ];
		for ( int i = 0; i < res.length; i++ ) {

			System.out.print( res[ i ] + ", " );

			State s = states.get( res[ i ] );

			Slice.extrude( out, s.con.out, height, height + delta );
			Slice.capAtHeight( out, s.con.out, false, height );
			Slice.capAtHeight( out, s.con.out, true, height + delta );

			height += delta;
		}
		out.dump( outfile );
	}


	private static double align( LoopL<Point2d> top, LoopL<Point2d> bottom ) {

		if (top.isEmpty() || bottom.isEmpty())
			return Double.MAX_VALUE;

		if (top == bottom)
			return 0;
		
		LoopL bbackwards = new LoopL<>( bottom );
		bbackwards.reverseEachLoop();

		return Math.sqrt( Math.abs( Loopz.area( Loopz.insideOutside( top, bbackwards ) ) ) );
	}

	private static double dataFit( LineSoup filtered, LoopL<Point2d> out ) {

		if ( out.isEmpty() || filtered.all.isEmpty() )
			return Double.MAX_VALUE;

		double totalDist = 0;

		for ( Line sl : filtered.all ) {

			double bestDist = Double.MAX_VALUE;

			for ( Loop<Point2d> loop : out )
				for ( Loopable<Point2d> ll : loop.loopableIterator() )
					bestDist = Math.min( bestDist, new Line( ll.get(), ll.getNext().get() ).distance( sl ) );

			totalDist += bestDist;
		}

		return totalDist / filtered.all.size();
	}
	
	public static int[] sliceOptimize( int numSlices, double[][] data, double[][] alignment ) {
		try {
			GRBEnv env = new GRBEnv( "mip1.log" );
			GRBModel model = new GRBModel( env );

			GRBVar[][] xij = new GRBVar[numSlices][numSlices];

			for ( int i = 0; i < numSlices; i++ )
				for ( int j = 0; j < numSlices; j++ )
					xij[ i ][ j ] = model.addVar( 0.0, 1.0, 1.0, GRB.BINARY, "x" + i + "_" + j );

			// overlap term n^2 terms
			for ( int i = 0; i < numSlices; i++ ) {
				GRBLinExpr expr = new GRBLinExpr();

				for ( int j = 0; j < numSlices; j++ )
					expr.addTerm( 1, xij[ i ][ j ] );

				model.addConstr( expr, GRB.EQUAL, 1, "overlap_" + i );
			}
			
//			for ( int i = 0; i < numSlices-1; i++ ) {
//				for ( int j = 0; j < numSlices; j++ ) {
//					GRBLinExpr expr = new GRBLinExpr();
//					expr.addTerm(  1, xij[ i   ] [ j ] );
//					expr.addTerm( -1, xij[ i+1 ] [ j ] );
//					model.addConstr( expr, GRB.EQUAL, 0, "super_adjacency" + j );
//				}
//			}

			// data fitting term n^2 terms
			GRBQuadExpr target = new GRBQuadExpr();
			for ( int i = 0; i < numSlices; i++ ) {
				for ( int j = 0; j < numSlices; j++ ) {
					if ( data[ i ][ j ] != Double.MAX_VALUE )
						target.addTerm( data[ i ][ j ] * 30, xij[ i ][ j ] );
					else
						target.addTerm( 1e3, xij[ i ][ j ] );
				}
			}

			// neighbour alignment n^3 quadratic terms
			for ( int i = 0; i < numSlices - 1; i++ )
				for ( int ja = 0; ja < numSlices; ja++ )
					for ( int jb = 0; jb < numSlices; jb++ )
						target.addTerm( alignment[ ja ][ jb ] == Double.MAX_VALUE ? 1e2 : alignment[ ja ][ jb ], xij[ i ][ ja ], xij[ i + 1 ][ jb ] );

			model.setObjective( target, GRB.MINIMIZE );
			model.getEnv().set( GRB.DoubleParam.TimeLimit, 10.0 );
			model.optimize();
			
//			xij[0][0].set( DoubleAttr.X, 1 );
//			target.getValue();
			
			System.out.println( "Obj: " + model.get( GRB.DoubleAttr.ObjVal ) );

			int[] out = new int[numSlices];

			for ( int i = 0; i < numSlices; i++ ) {
				System.out.print( "i: " + i + " " );
				for ( int j = 0; j < numSlices; j++ ) {
					System.out.print( xij[ i ][ j ].get( GRB.DoubleAttr.X ) + " " );
					if ( xij[ i ][ j ].get( GRB.DoubleAttr.X ) == 1 )
						out[ i ] = j;
				}
				System.out.println();
			}

			// Dispose of model and environment

			model.dispose();
			env.dispose();
			return out;

		} catch ( GRBException e ) {
			System.out.println( "Error code: " + e.getErrorCode() + ". " + e.getMessage() );
			e.printStackTrace();
		}
		return null;
	}

	private void slicesByDataFit( Slice slice, double delta, List<State> states ) {
		{
			Cache<State, Double> cache = new Cache<State, Double>() {

				@Override
				public Double create( State i ) {

					double out = 0;

					for ( State s : states ) {
						double d =dataFit( s.filtered, i.con.out );
						if (d != Double.MAX_VALUE)
							out += d;
						else
							out += 1e3;
					}

					if (out == 0)
						out = Double.MAX_VALUE;
					
					return out;
				}
			};

			Collections.sort( states, new Comparator<State>() {
				public int compare( State o1, State o2 ) {
					return -Double.compare( cache.get( o1 ), cache.get( o2 ) );
				};
			} );

			
			states.stream().forEach( s -> CutHoles.cutHoles(s.con.out, P.CON_TOL/5) );

			
			ObjDump out = new ObjDump();

			double height = slice.min[ slice.majorAxis ];
			for ( int i = 0; i < states.size(); i++ ) {

				State s = states.get(i);
				
				System.out.println(" >>>> " + cache.get(s));
				
				Slice.extrude( out, s.con.out, height, height + delta );
				Slice.capAtHeight( out, s.con.out, false, height );
				Slice.capAtHeight( out, s.con.out, true, height + delta );

				height += delta * 3;
			}
			out.dump( outfile );
		}
	}
}
