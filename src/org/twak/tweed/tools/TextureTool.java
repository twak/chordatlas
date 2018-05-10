package org.twak.tweed.tools;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.ImagePlaneGen;
import org.twak.tweed.gen.Pano;
import org.twak.tweed.gen.PanoGen;
import org.twak.tweed.gen.PlanesGen;
import org.twak.utils.Imagez;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.FacadeFinder.FacadeMode;
import org.twak.viewTrace.FacadeFinder.ToProjMega;
import org.twak.viewTrace.FacadeFinder.ToProject;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;

public class TextureTool extends SelectTool {

	public static final String LINE_XML = "line.xml";
	public boolean singleFolder = true;
	long lastClick = 0;
//	FacadeFinder ff;

	public TextureTool( Tweed tweed ) {
		super( tweed );
	}

	@Override
	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {
		if ( System.currentTimeMillis() - lastClick > 500 ) {

			Object[] directHandler = target.getUserData( ClickMe.class.getSimpleName() );

			if ( directHandler != null )
				( (ClickMe) directHandler[ 0 ] ).clicked( target );

			lastClick = System.currentTimeMillis();
		}
	}

	@Override
	public String getName() {
		return "texture";
	}

	@Override
	public void getUI( JPanel panel ) {

		panel.setLayout( new ListDownLayout() );
		panel.add( new JLabel( "right click on an object to texture" ) );
	}
}
