package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Point2d;

import org.twak.utils.collections.Arrayz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.DRectangle.RectDir;
import org.twak.viewTrace.facades.MiniFacade.Feature;

/**
 * Old code for greebling via CGA-esque grammar
 * 
 * @author twak
 *
 */
public class CGAMini extends FeatureGenerator {

	public CGAMini( MiniFacade mf) {
		super(mf);
		update();
	}

	private static List<Double> split3 (RectDir r, double first, double last) {
		
		double wight = r.dirX ? r.rect.width : r.rect.height;
		
		List<Double> out = new ArrayList<>();
		
		if (first + last > wight) {
			out.add (first * wight / (first + last) );
			out.add (last * wight / (first + last) );
		}
		else {
			out.add(first);
			out.add (wight - first - last);
			out.add(last);
		}
		
		return out;
	}

	private static List<Double> split1 (RectDir r, double first ) {
		
		double wight = r.dirX ? r.rect.width : r.rect.height;
		
		List<Double> out = new ArrayList<>();
		
		if (first  > wight) 
			out.add (wight);
		else if (first > 0){
			out.add(first);
			out.add (wight - first );
		} else {
			out.add (wight - first );
			out.add(first);
		}
		
		return out;
	}
	
	
	private static List<Double> split3Y (RectDir r, double first, double last) {
		
		double wight =  r.dirX ? r.rect.width : r.rect.height;
		
		List<Double> out = new ArrayList<>();
		
		if (first + last > wight) {
			out.add (first * wight / (first + last) );
			out.add (last * wight / (first + last) );
		}
		else {
			out.add(first);
			out.add (wight - first - last);
			out.add(last);
		}
		
		return out;
	}
	
	private static List<Double> splitFloors (RectDir r, double ground, double middle, double top) {
		
		double wight = r.dirX ? r.rect.width : r.rect.height;
		
		List<Double> out = new ArrayList<>();
		
		if (ground + top > wight) {
			
			if (ground > wight) {
				out.add (wight);
				return out;
			} else {
				out.add (ground );
				out.add ( wight - ground );
			}
		}
		else {
			
			out.add(ground);
			
			int count = Math.max(1, (int)((wight - top - ground) / middle ) );
			double delta = (wight - ground-top) / count;
			
			if (delta < top) {
				out .add (wight - ground);
				return out;
			}
			
			for (int c = 0; c < count ; c++ ) {
				out.add(delta);
			}
			
			out.add(top);
		}
		
		return out;
	}

	private static List<Double> stripe( RectDir r, double win, double gap_ ) {
		
		double weight =  r.dirX ? r.rect.width : r.rect.height;
		List<Double> out = new ArrayList<>();
		
		int count = (int) ( (weight - win) / (win+gap_ ) ) + 1;
		
		if (count == 0) {
			out.add (weight);
			return out;
		}
		
		double gap;
		if (count == 1)
			gap = -1;
		else
			gap = ( weight - ( win * count) ) / (count - 1);
		
		for (int i = 0; i < count ; i++) {
			out.add(0.+win);
			if (i != count-1)
				out.add(gap);
		}
		
		return out;
	}

	protected boolean visible( DRectangle dRectangle, List<DRectangle> occlusions ) {
		
		for (Point2d p : dRectangle.points()) 
			for (DRectangle d : occlusions)
				if (d.contains( p ))
					return false;
		
		return true;
	}
	
	public void update () {

		DRectangle all = mf.getAsRect();
		
		List<DRectangle> occlusions = Collections.EMPTY_LIST;
		
		double groundFloorHeight = 0;

		List<DRectangle> floors = all.splitY( r -> splitFloors( r, 3, 2.5, 2 ) );

		clear();
		
		mf.color = Arrayz.toDoubleArray( GreebleSkel.BLANK_WALL ); 
		
		for ( int f = 0; f < floors.size(); f++ ) {

			boolean isGround = f == 0;
			
			DRectangle floor = floors.get( f );
			List<DRectangle> edges = floor.splitX( r -> split3( r, 1, 1 ) );
			
			if (isGround)
				groundFloorHeight = floor.height;

			if ( edges.size() != 3 ) {

			} else {
				

				DRectangle cen = edges.get(1);
				
				if ( cen.height > 1.8 ) {

					if ( f == 0  ) {

						List<DRectangle> groundPanel = cen.splitX( r -> split1( r, 0.9 ) );

						if ( groundPanel.get( 0 ).width < 0.7 ) {
							
						}
						else if ( true /* door */ ) {
							
							List<DRectangle> doorHeight = groundPanel.get( 0 ).splitY( r -> split1( r, 2.2 ) );

							if (visible(  doorHeight.get( 0 ), occlusions ))
								add( Feature.DOOR, doorHeight.get( 0 ) );

							if ( groundPanel.size() > 1 ) {

								List<DRectangle> gWindowPanelH = groundPanel.get( 1 ).splitX( r -> split3( r, 0.5, 0.0 ) );
								if ( gWindowPanelH.size() > 2 ) {
									List<DRectangle> gWindowPanelV = gWindowPanelH.get( 1 ).splitY( r -> split3( r, 0.5, 0.5 ) );
									if ( gWindowPanelV.size() > 2 ) {
										
										if (visible( gWindowPanelV.get(1), occlusions ))
											add( Feature.SHOP, gWindowPanelV.get( 1 ) );
											
									} 
								}
							}
							
						}
						else
							windowStrip( cen, Math.random() < 0.5, occlusions );


					} else {

						windowStrip( cen, 
								f > 0 && f < floors.size() - 1 &&  Math.random() < 0.5, occlusions );
						
						if (f == 1 )
							add( Feature.MOULDING, new DRectangle (floor.x + 0.1, floor.y,  floor.width - 0.2, 0.5 ) );
					}
				}
			}
		}
	}

	private void windowStrip( DRectangle cen, boolean makeBalcony, List<DRectangle> occlusions ) {
		
		if ( cen.width < 0.7 ) 
			return;
		
		List<DRectangle> fPanels = cen.splitX( r -> stripe( r, 1.5, 0.8 ) );

		for ( int p = 0; p < fPanels.size(); p++ ) {
			if ( p % 2 == 0 ) {

				List<DRectangle> winPanel = fPanels.get( p ).splitY( r -> split3Y( r, 1, 0.2 ) );

				if ( winPanel.size() == 3 ) {

					if (visible (winPanel.get(1), occlusions)) {
						
						DRectangle window = winPanel.get( 1 );
						
						add ( Feature.WINDOW, window );
						
						DRectangle sill = new DRectangle(window);
						sill.height = 0.2;
						sill.y -= sill.height;
						add ( Feature.SILL, sill );
						
						DRectangle lintel = new DRectangle(sill);
						sill.y = window.getMaxY();
						add ( Feature.CORNICE, sill );
						
						if ( makeBalcony ) {
							DRectangle balcony = new DRectangle( winPanel.get( 1 ) );
							balcony.height = winPanel.get( 1 ).height / 3;
							balcony.x -= 0.15;
							balcony.width += 0.3;
							add( Feature.BALCONY, balcony );
						}
					}
				} 
			} 
		}
	}
}
