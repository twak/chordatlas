package org.twak.tweed.gen;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.vecmath.Vector2d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.Rainbow;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

public class WindowGen extends Gen implements IDumpObjs {

	public transient List<List<Window>> windows = new ArrayList();

	BlockGen block;
	
	public static class Window {
		
		Vector3f loc, along, up;
		float width, height, depth;
		
		double panelWidth, panelHeight;
		
		public Window( Vector3f location, Vector3f along, Vector3f up, double width, double height, double depth,
				double panelWidth, double panelHeight ) {
			
			this.width  = (float) width;
			this.height = (float) height;
			this.depth  = (float) depth;
			this.panelHeight = panelHeight;
			this.panelWidth  = panelWidth;
			
			this.loc = location;
			this.along = along;
			this.up = up;
		}
	}

	public WindowGen( Tweed tweed, BlockGen block ) {
		super( "windows", tweed );
		this.block= block;
	}
	

	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();
		
		
		FeatureCache fg = tweed.features;
		
		int i = 0;

		
		if (fg != null) {
			
			for (MegaFeatures mf :  fg.getBlock( block.center ).features  ) {
				
				Vector3f along, up, in ;
				{
					Vector2d mfl = mf.megafacade.dir();
					mfl.normalize();
					along = new Vector3f((float) mfl.x, 0, (float) mfl.y);
					
					up = new Vector3f(0,1,0);
					in = along.cross(up);
				}
				
				Vector3f mfStart = Jme3z.to( Pointz.to3( mf.megafacade.start ) );
				
				for (ImageFeatures im : mf.features) {
					
					MeshBuilder mb = new MeshBuilder();
					
					float offset = (float) Math.random() * 5;
					
					for (MiniFacade mini : im.miniFacades) {
						for (DRectangle r : mini.rects.get(Feature.WINDOW)) {
						
							Vector3f loc = new Vector3f( mfStart );

							loc.addLocal( along.mult( (float) r.x ) );
							loc.addLocal( up   .mult( (float) r.y ) );
							
							createWindow( mb, mb, new Window(loc.add(in.mult(offset+ 0.4f) ), along, up, r.width, r.height, 0.3, 0.4, 0.6 ) );
						}
						
						
						if (!mini.rects.get(Feature.WINDOW).isEmpty()) {

							Vector3f loc = new Vector3f(mfStart);
							loc.addLocal(along.mult((float) mini.left)).addLocal(in.mult(offset) );
							mb.addCube(loc, up, along, in, (float) mini.height, (float) mini.width, 0.1f);
						}
					}
					
					Geometry g = new Geometry( this.getClass().getSimpleName() );
					g.setMesh( mb.getMesh() );
					Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
					
					mat.setColor( "Diffuse", Jme3z.toJme(Rainbow.getColour(i)) );
					mat.setColor( "Ambient", Jme3z.toJme(Rainbow.getColour(i)) );
					
					mat.setBoolean( "UseMaterialColors", true );
					
					g.setMaterial( mat );
					i++;
					
					gNode.attachChild( g );
				}
			}
		}

		super.calculate();
	}


	public static void createWindow( MeshBuilder wood, MeshBuilder glass, Window w ) {
			
			Vector3f along = new Vector3f( w.along );
			along.normalizeLocal();
			Vector3f up = new Vector3f( w.up );
			up.normalizeLocal();
			Vector3f norm = along.cross( up );
			
			createWindow( wood, glass, w, along, up, norm );
	}


	private static void createWindow( MeshBuilder wood, MeshBuilder glass, Window w, 
			Vector3f along, Vector3f up, Vector3f norm ) {
		
		
		float small = 0.05f;
		
		glass.addCube( w.loc.add(up.mult(small)).add(along.mult( small )), up, along, norm, w.height - small * 2, w.width - small * 2,  0.1f );
		wood.addCube( w.loc.subtract(norm.mult(w.depth + small)), up, along, norm, w.height, w.width, w.depth + 0.1f );
		
		float fWidth = 0.07f;
		float f2Width = 0.05f;
		
		// bottom, top
		wood.addCube( w.loc, up, along, norm, fWidth, w.width, 0.2f );
		wood.addCube( w.loc.add(up.mult(w.height - fWidth)) , up, along, norm, fWidth, w.width, 0.2f );
		
		
		// sides
		wood.addCube( w.loc, up, along, norm, w.height, fWidth, 0.2f );
		wood.addCube( w.loc.add(along.mult(w.width-fWidth)), up, along, norm, w.height, fWidth, 0.2f );
		
		
		// cross
		int xc = (int)(w.width / w.panelWidth);
		for (int i = 0; i < xc; i++) 
			wood.addCube( w.loc.add(along.mult(w.width * (i+1) / (xc+1)-f2Width/2)), up, along, norm, w.height, f2Width, 0.15f );
		
		int yc = (int) (w.height / w.panelHeight);
		for (int i = 0; i < yc; i++) 
			wood.addCube( w.loc.add(up.mult(w.height * (i+1) / (yc+1) -f2Width/2)), up, along, norm, f2Width, w.width, 0.15f );
		
	}
		
	@Override
	public JComponent getUI() {
		return new JLabel( ";)" );
	}


	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump(dump, gNode, 0);
	}

}
