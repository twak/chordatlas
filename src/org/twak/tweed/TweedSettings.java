package org.twak.tweed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.vecmath.Matrix4d;

import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.ICanSave;
import org.twak.utils.Filez;
import org.twak.utils.ui.auto.Auto;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

public class TweedSettings {

	
	
	public static TweedSettings settings = new TweedSettings();
	public static RecentFiles recentFiles;
	static File folder; // location of data file
	
	public String bikeGanRoot = new File (System.getProperty("user.home")+"/code/bikegan").getAbsolutePath();//"/home/twak/code/bikegan";
//	public String egNetworkInputs = "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/";
	
	public Vector3f cameraLocation = new Vector3f(575.0763f, 159.23715f, -580.0377f);
	public Quaternion cameraOrientation = new Quaternion(0.029748844f, 0.9702514f, -0.16988836f, 0.16989778f);
	@Auto.Ignore
	public int cameraSpeed = 0;

//	public double trans[] = new double[] { 982744.4803613932, 201433.17395506793 };  // ny
//	public String gmlCoordSystem = "EPSG:2263";
//	public double trans[] = new double[] { 440435.9,4473718.3 }; // madrid
//	public String gmlCoordSystem = "EPSG:3042";
//	public double trans[] = new double[] { 529665.78, 181912.16 }; // regent
//	public String gmlCoordSystem = "EPSG:27700"; 
//	public double trans[] = new double[] { 261826.04,665079.33 }; // glasgow
//	public String gmlCoordSystem = "EPSG:27700"; 
//	public double trans[] = new double[] { 121659.721974586034776,486774.576303347887006 }; // amsterdam
//	public String gmlCoordSystem = "EPSG:28992";
//	public double trans[] = new double[] { 426138.059429821,975725.769060029 }; // oviedo
//	public String gmlCoordSystem = "EPSG:2062"; 
//	public double trans[] = new double[] { 13506158.343432899564505,326522.905302504834253 }; // detroit
//	public String gmlCoordSystem = "EPSG:2253"; 

	public double trans[] = null; // edit this to set GIS offset before creating new workspace (above are twak's custom offsets!) 
	@Auto.Ignore
	public String gmlCoordSystem = null;
	public Matrix4d toOrigin, fromOrigin;

	public boolean flipFootprints = true;
	public double ambient = 0.5;
	@Auto.Ignore
	public boolean ortho = false;
	@Auto.Ignore
	public int fov = 0;

	public boolean calculateFootprintNormals = true;
	public double  snapFootprintVert = 0;
	public boolean SSAO = true;
	public boolean shadows = false;
	
	public double blockMeshPadding = 5;
	
	public double megaFacadeAreaThreshold = 30; // 23 for regent
	public double profileHSampleDist = 0.2;
	public double profileVSampleDist = 0.5;
	public double profilePrune = 0.3;
	public double meshHoleJumpSize = 3;
	public double badGeomDist = 1.5;
	public double badGeomAngle = 0.5; // radians
	public double miniSoftTol = 2.5;
	
	public boolean useGis = true;
	public double  miniWidthThreshold = 2;
	public int     profileCount = 30;
	public double  exposedFaceThreshold = 0.4;
	public double  heightThreshold = 4;
	public double  gisThreshold = 0.8;
	public double  megafacacadeClusterGradient = 3;
	public double  lowOccluderFilter = 4; // "bus filter"
	public boolean snapFacadeWidth = true;
	public boolean useGreedyProfiles = false;
	public boolean roofColours = true;
	
	public List<Gen> genList = new ArrayList<>();
	public boolean LOD = true;
	public boolean createDormers = true;
	public double superResolutionBlend = 0.4;
	public boolean siteplanInteractiveTextures = false;
	public boolean importMiniMeshTextures = false;
	

	
	
	public TweedSettings() {
	}

	public static void load( File folder ) {
		
		if (!folder.isDirectory())
			folder = folder.getParentFile();
		
		TweedSettings.folder = folder;
		
		try {
			
			File def = new File( folder, "tweed.xml" );
			
			if (!def.exists())
				settings = new TweedSettings();
			else
			{
				XStream xs = new XStream(new PureJavaReflectionProvider());
				xs.ignoreUnknownElements();
				settings = (TweedSettings) xs.fromXML( def );
			}
			
			
			File defaultData = new File (folder, "chordatlas_example_inputs_1.zip");
			if (!defaultData.exists()) {
				ProgressMonitor pm = new ProgressMonitor( null, "downloading project data", "...", 0, 1 );
				Filez.unpackArchive( new URL( "http://geometry.cs.ucl.ac.uk/projects/2018/frankengan/data/"+defaultData.getName() ), folder, pm );
			}
			
			TweedFrame.instance.tweed.initFrom( folder.toString() );
			
		} catch ( Throwable th ) {
			settings = new TweedSettings();
			save(true);
			th.printStackTrace();
		}
		
		writeRecentFiles();
	}

	public static void save(boolean backup) {
		
		if (folder != null) {
			
			settings.genList = TweedFrame.instance.genList.stream().filter( g -> g instanceof ICanSave ).collect( Collectors.toList() );
			
			FileOutputStream fos = null;
			
			settings.cameraOrientation = TweedFrame.instance.tweed.oldCameraRot;
			settings.cameraLocation = TweedFrame.instance.tweed.oldCameraLoc;
			
			try {
				fos = new FileOutputStream( new File( folder, "tweed.xml" +(backup ? "_backup" : "") ) );
				new XStream(new PureJavaReflectionProvider()).toXML( TweedSettings.settings, fos );
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
			finally {
				if (fos != null)
					try {
						fos.close();
					} catch ( IOException e ) {
						e.printStackTrace();
					}	
			}
			
			if (!backup)
				writeRecentFiles();
			
		}
		else if (!backup) 
			JOptionPane.showMessageDialog( null, "save failed" );
	}
	
	private static File RECENT_FILE_LOCATION  = new File ( System.getProperty("user.home") +File.separator+".tweed_config");
	
	public static void writeRecentFiles() {
		
		if (folder == null)
			return;
		
		recentFiles.f.remove( folder );
		
//		if (recentFiles.f.isEmpty() || !recentFiles.f.contains (folder) ) {
			recentFiles.f.add( 0, folder );
			
			while (recentFiles.f.size() > 20)
				recentFiles.f.remove( recentFiles.f.size() - 1 );
			try {
				new XStream().toXML( recentFiles, new FileOutputStream( RECENT_FILE_LOCATION ) );
			} catch ( FileNotFoundException e ) {
				e.printStackTrace();
			}
//		}
	}

	public static void loadDefault() {

		if (recentFiles == null) {
			try {
				recentFiles = (RecentFiles) new XStream().fromXML( RECENT_FILE_LOCATION );
			}
			catch (Throwable th) {
				System.out.println( "couldn't load recent project list" );
				recentFiles = new RecentFiles();
			}
			
			Set<File> hs = new LinkedHashSet<>();
			hs.addAll(recentFiles.f);
			recentFiles.f.clear();
			recentFiles.f.addAll(hs);
		}
		
		if (!recentFiles.f.isEmpty()) {
			File last = recentFiles.f.get( 0 );
			if (last.exists())
				load( last );
			else {
				JOptionPane.showMessageDialog( null, "Can't find last project: \"" + last.getName()+"\"" );
				recentFiles.f.remove( 0 );
			}
		}
	}

	public void resetTrans() {
		this.trans = new double[] {0,0};
		this.gmlCoordSystem = null;
		this.toOrigin = new Matrix4d();
		this.toOrigin.setIdentity();
		this.fromOrigin = new Matrix4d();
		this.fromOrigin.setIdentity();
	}
	

}
