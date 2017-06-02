package org.twak.tweed.tools;

import javax.vecmath.Point3d;

import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.utils.collections.LoopL;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;


public class SelectTool extends Tool {

	public SelectTool( Tweed tweed ) {
		super( tweed );
	}

	long lastClick = 0;

	@Override
	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {
		if ( System.currentTimeMillis() - lastClick > 500 ) {

			
			do {
				
				Object[] directHandler = target.getUserData( ClickMe.class.getSimpleName() );

				if ( directHandler != null ) {
					( (ClickMe) directHandler[ 0 ] ).clicked( vector3f );
					break;
				}

				target = target.getParent();
				
			} while ( target != null );
			
			lastClick = System.currentTimeMillis();
		}
	}

	@Override
	public String getName () {
		return "select";
	}
	
	public void selected( LoopL<Point3d> list ) {}
}
