package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.dbg.Plot;
import org.twak.tweed.gen.ProfileGen.MegaFacade;
import org.twak.utils.PaintThing;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.viewTrace.SuperLine;

public class ProfileAssignmentViewer {

	public ProfileAssignmentViewer( SuperFace sf, List<Prof> globalProfs ) {

		JSlider edge = new JSlider (-1, sf.edgeCount()-1);
		JButton go = new JButton("GO!");
		
		Plot plot = new Plot( edge, go);
		
		edge.addChangeListener(  e -> go.doClick() );

		go.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent ae ) {

				PaintThing.debug.clear();

				int i = 0;
				for ( HalfEdge e : sf ) {
					
					PaintThing.debug( Color.black, 1, e.line() );
					
					if ( edge.getValue() == -1 || i == edge.getValue() ) {
						SuperEdge se = (SuperEdge) e;
						Prof bestProf = null;
						SuperLine sl = (SuperLine) se.profLine;

						if ( sl != null ) {

							List<Prof> profs = new ArrayList<>();
							MegaFacade mf = sl.getMega();

							int start = mf.getIndex( se.start ), end = mf.getIndex( se.end ) + 1;

							for ( int ii = start; ii <= end; ii++ ) {
								Prof p2 = mf.profiles.get( ii );
								if ( p2 != null ) {
									PaintThing.debug( new Color( 0, 0, 0, 60 ), 1, p2 );
									profs.add( p2 );
								}
							}
									
							double bestScore = Double.MAX_VALUE;
							//							for ( int ii = 0; ii < profFit.get( se ).length; ii++ ) {

							for ( Prof p : globalProfs ) {

								Double d = SkelFootprint.meanDistance( mf, mf.getIndex( se.start ), mf.getIndex( se.end ), p );

								//								double d = profFit.get( se )[ ii ];
								if ( d != null && d < bestScore ) {
									bestScore = d;
									bestProf = p;
								}
							}

							if ( bestProf != null ) {
								PaintThing.debug( Color.red, 3, bestProf );

								if ( se.prof != null )
									PaintThing.debug( Color.green, 1, se.prof );

							}

							PaintThing.debug.put( 1, "score " + bestScore );

							if ( edge.getValue() == -1 )
								PaintThing.debug.put( 1, "global" );
							else
								PaintThing.debug.put( 1, "count " + profs.size() );
							
						}
						PaintThing.debug( Color.black, 3, e.line() );
//							break;
						}
						i++;
					
						
						
					}
				
					plot.repaint();
					}
		} );
		
		go.doClick();
	}
//		PaintThing.debug.clear();
//		PaintThing.debug( new Color(0,0,0,100), 1, globalProfs );	}

}
