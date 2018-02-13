package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;

import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.Tweed;
import org.twak.utils.Cache;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.DRectangle.RectDir;

/**
 * Old code for greebling via CGA-esque grammar
 * 
 * @author twak
 *
 */
public class CGA extends GreebleSkel {

	public CGA( Tweed tweed ) {
		super( tweed );
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

	
	protected double cga ( Loop<Point2d> rect, Matrix4d to3d, WallTag wallTag, 
			MeshBuilder normalWall, MeshBuilder ground, 
			Cache<float[], MeshBuilder> mbs,
			List<DRectangle> occlusions ) {

		double[] bounds = Loopz.minMax2d( rect );
		
		GreebleGrid gg = new GreebleGrid( tweed, new MMeshBuilderCache() );
		
		DRectangle all = new DRectangle(
				bounds[0], 
				bounds[2], 
				bounds[1] - bounds[0], 
				bounds[3] - bounds[2] );
		
//			for (DRectangle d : occlusions)
//				mbs.get( new float[] {1,1,0,1} ).add( d, to3d );
			
		float[] windowColor     = new float [] {0.8f, .8f,0.8f, 1},
				glassColor      = new float [] {0.3f, 0.3f, 1, 1},
				mouldingColor   = new float [] {0.7f, .7f,0.7f, 1};
		
		
		double groundFloorHeight = 0;

		List<DRectangle> floors = all.splitY( r -> splitFloors( r, 3, 2.5, 2 ) );

		
		for ( int f = 0; f < floors.size(); f++ ) {

			boolean isGround = f == 0 && wallTag.isGroundFloor;
			
			DRectangle floor = floors.get( f );
			MeshBuilder wall =isGround ? ground : normalWall;
			List<DRectangle> edges = floor.splitX( r -> split3( r, 1, 1 ) );
			
			if (isGround)
				groundFloorHeight = floor.height;

			if ( edges.size() != 3 ) {
				wall.add( floor, to3d );

			} else {
				
				
				wall.add( edges.get( 0 ), to3d );
				wall.add( edges.get( edges.size() - 1 ), to3d );

				DRectangle cen = edges.get(1);
				
				if ( cen.height < 1.8 )
					wall.add( cen, to3d );
				else {

					if ( f == 0 && wallTag.isGroundFloor ) {

						List<DRectangle> groundPanel = cen.splitX( r -> split1( r, 0.9 ) );

						if ( groundPanel.get( 0 ).width < 0.7 )
							wall.add( groundPanel.get( 0 ), to3d );
						
						else if (wallTag.makeDoor) {
							
							List<DRectangle> doorHeight = groundPanel.get( 0 ).splitY( r -> split1( r, 2.2 ) );

							if (visible(  doorHeight.get( 0 ), occlusions ))
								greebleGrid.createDoor( doorHeight.get( 0 ), to3d, wall, mbs.get( windowColor ), wallTag.doorDepth );
							else
								wall.add (doorHeight.get(0), to3d);

							if ( doorHeight.size() > 1 )
								wall.add( doorHeight.get( 1 ), to3d );

							if ( groundPanel.size() > 1 ) {

								List<DRectangle> gWindowPanelH = groundPanel.get( 1 ).splitX( r -> split3( r, 0.5, 0.0 ) );
								if ( gWindowPanelH.size() > 2 ) {
									wall.add( gWindowPanelH.get( 0 ), to3d );
									wall.add( gWindowPanelH.get( 2 ), to3d );
									List<DRectangle> gWindowPanelV = gWindowPanelH.get( 1 ).splitY( r -> split3( r, 0.5, 0.5 ) );
									if ( gWindowPanelV.size() > 2 ) {
										wall.add( gWindowPanelV.get( 0 ), to3d );
										wall.add( gWindowPanelV.get( 2 ), to3d );
										
										if (visible( gWindowPanelV.get(1), occlusions ))
											greebleGrid.createWindow( gWindowPanelV.get( 1 ), to3d, wall, 
													mbs.get( windowColor ), 
													mbs.get( glassColor ), 
													wallTag.windowDepth,
													-1, -1, -1, 0.6, 0.9);
										else
											wall.add( gWindowPanelV.get( 1 ), to3d );
											
									} else
										for ( DRectangle d : gWindowPanelV )
											wall.add( d, to3d );
								} else
									for ( DRectangle d : gWindowPanelH )
										wall.add( d, to3d );

							}
							
						}
						else
							windowStrip( to3d, wallTag, mbs .get( windowColor), mbs.get( glassColor ), wall, cen, false, occlusions, greebleGrid );


					} else {

						windowStrip( to3d, wallTag, mbs .get( windowColor), mbs.get( glassColor ), wall, cen, 
								f > 0 && f < floors.size() - 1 && wallTag.makeBalcony, occlusions, greebleGrid );
						
						if (f == 1 && wallTag.isGroundFloor)
							greebleGrid.moulding ( to3d,  new DRectangle (floor.x, floor.y,  floor.width, 0.5 ), mbs.get(mouldingColor));
						
					}
				}
			}
		}
		
		return groundFloorHeight;
	}

	private void windowStrip( Matrix4d to3d, WallTag wallTag,   MeshBuilder windowColor, 
			MeshBuilder glassColor, MeshBuilder wall, DRectangle cen, 
			boolean makeBalcony, List<DRectangle> occlusions, GreebleGrid gg ) {
		
		if ( cen.width < 0.7 ) {
			wall.add( cen, to3d );
			return;
		}
		
		List<DRectangle> fPanels = cen.splitX( r -> stripe( r, 1.5, 0.8 ) );

		for ( int p = 0; p < fPanels.size(); p++ ) {
			if ( p % 2 == 0 ) {

				List<DRectangle> winPanel = fPanels.get( p ).splitY( r -> split3Y( r, 1, 0.2 ) );

				if ( winPanel.size() == 3 ) {

					wall.add( winPanel.get( 0 ), to3d );
					wall.add( winPanel.get( 2 ), to3d );

					
					if (visible (winPanel.get(1), occlusions)) {
						gg.createWindow( winPanel.get( 1 ), to3d, wall, windowColor, glassColor, wallTag.windowDepth, 
								(float) wallTag.sillDepth, (float) wallTag.sillHeight, -1, 0.6, 0.9 );
						if ( makeBalcony ) {
							DRectangle balcony = new DRectangle( winPanel.get( 1 ) );
							balcony.height = wallTag.balconyHeight;
							balcony.x -= 0.15;
							balcony.width += 0.3;
							gg.createBalcony( balcony, to3d, glassColor, wallTag.balconyDepth );
						}
					}
					else
						wall.add (winPanel.get(1), to3d );
					
					
					
				} else
					wall.add( fPanels.get( p ), to3d );
			} else {
				wall.add( fPanels.get( p ), to3d );
			}
		}
	}
}
