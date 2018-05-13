package org.twak.tweed.gen.skel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.twak.siteplan.tags.PlanTag;
import org.twak.utils.Line;
import org.twak.utils.ui.Colourz;
import org.twak.viewTrace.facades.MiniFacade;

public class WallTag extends PlanTag{
	
	public Line planLine;
//	public float[] color = null;//new float[] {228/255f, 223/255f, 206/255f, 1.0f };
	public boolean isGroundFloor, makeDoor = Math.random() < 0.5;
	public double windowDepth = 0.5;//Math.random() * 0.3 + 0.4;
	public double doorDepth = 0.4;
	public float[] groundFloorColor;
	public double sillHeight = -1, sillDepth = -1;
	public double balconyHeight = 1;//0.6 + Math.random() * 0.5;
	public double balconyDepth = 0.7;//0.2 + Math.random() * 1;
	public boolean makeBalcony = Math.random() < 0.4;
	public float corniceHeight = 0.3f;
	public double noWindowsBelow = 0;
	public MiniFacade miniFacade;

	public Object occlusionID; // markers for which other tags may occlude
	public Set<Object> occlusions = new HashSet<>();
	
	public WallTag() {
		super("wall");
	}
	
	
	
	public WallTag(Line planLine, Object occlusionID, Set<Object> occlusions, MiniFacade miniFacade) {
		super( "wall" );
		
		this.occlusions = occlusions;
		this.occlusionID = occlusionID;
		
		this.planLine = planLine;
		this.miniFacade = miniFacade;

//		if (this.miniFacade != null ) {
//			if ( this.miniFacade.color != null)
//				float[] c = Colour.toF4( miniFacade.app.color );
//				
//				this.color = new float[] { 
//					(float) c[ 0 ], 
//					(float) c[ 1 ], 
//					(float) c[ 2 ], 1f };
			
//			if (this.miniFacade.groundColor != null && ! Arrays.equals( miniFacade.color, miniFacade.groundColor ) )
//				this.groundFloorColor = new float[] { 
//				(float) miniFacade.groundColor[ 0 ], 
//				(float) miniFacade.groundColor[ 1 ], 
//				(float) miniFacade.groundColor[ 2 ], 1f };
//		}

		
//		else if (Math.random() < 0.7) {
//			
//			float hue = (float)Math.random();
//			Color c = new Color ( Color.HSBtoRGB( hue, 0.5f, 0.3f ) );
//			groundFloorColor = new float [] { 
//				(float) (c.getRed() / 255.), 
//				(float) (c.getGreen() / 255.), 
//				(float) (c.getBlue() / 255.), 
//				1};
//			
//			
//			c = new Color ( Color.HSBtoRGB( hue, 0.2f, 0.2f ) );
//			
//			color = new float [] { 
//					(float) (c.getRed() / 255.), 
//					(float) (c.getGreen() / 255.), 
//					(float) (c.getBlue() / 255.), 
//					1};
//		}
//		else
//		{
//			Color c = new Color ( Color.HSBtoRGB( (float)Math.random(), 0.3f, 0.2f ) );
//			color = new float [] { 
//					(float) (c.getRed() / 255.), 
//					(float) (c.getGreen() / 255.), 
//					(float) (c.getBlue() / 255.), 
//					1};
//			
//		}
		
		if (Math.random() < 0.7) {
			sillDepth = Math.max (0.1, Math.random() * 0.2);
			sillHeight = Math.max (0.1, Math.random() * 0.2);
		}
	}

	public WallTag( Line planline, Object occlusionID, Set<Object> occlusions, MiniFacade miniFacade, boolean isGroundFloor ) {
		this( planline, occlusionID, occlusions, miniFacade );
		this.isGroundFloor = isGroundFloor;
	}

	public WallTag( WallTag wtf, MiniFacade scaledMF ) {
		this (wtf.planLine, wtf.occlusionID, wtf.occlusions, scaledMF, wtf.isGroundFloor);
	}
}
