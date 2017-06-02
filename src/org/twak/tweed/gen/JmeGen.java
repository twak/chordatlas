package org.twak.tweed.gen;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.utils.geom.ObjDump;

import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;

public class JmeGen extends Gen implements IDumpObjs{

	Spatial spatial;
	
	public JmeGen( String name, Tweed tweed, Spatial spatial ) {
		super( name, tweed );
		this.spatial = spatial;
	}


	@Override
	public void calculate( ) {
		
		gNode.removeFromParent();

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		gNode.attachChild( spatial );

		gNode.updateModelBound();
		gNode.updateGeometricState();
	}

	@Override
	public JComponent getUI() {
		return new JLabel ("!");
	}		
	
	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump(dump, gNode, 0);
	}
}
