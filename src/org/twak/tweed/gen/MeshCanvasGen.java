package org.twak.tweed.gen;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import org.twak.tweed.Tweed;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.ui.Cancellable;

import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;

public class MeshCanvasGen extends ObjGen {

	public Pano pano;
//	static int TEX_SIZE[] = new int[] { 3000, 2000};
	transient Cancellable cancel;
	boolean autoProject = false;

	public MeshCanvasGen(String name, Tweed tweed, Pano pano) {
		super(name, tweed);
		this.pano = pano;
	}

	@Override
	public JComponent getUI() {
		JComponent out = super.getUI();

		JButton recalc = new JButton("recalculate");
		out.add(recalc);
		recalc.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						calculate();
						return null;
					}
				});
			}
		});
		
		final JCheckBox auto = new JCheckBox("auto project", autoProject);
		auto.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				autoProject = auto.isSelected();
				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						calculate();
						return null;
					}
				});
			}
		});
		out.add(auto);

		return out;
	}

	protected void setTexture(Geometry g, Material mat) {
//		setTexture(g, mat, new int[] { 2048,2048} /*preview size */, pano.getSmallPano(), new File("/home/twak/Desktop/foo.png") );
		setTexture(g, mat, new int[] {1024, 1024} /*preview size */, pano.getSmallPano(), new File( Tweed.SCRATCH + "foo.png") );
	}
	
	protected void renderTexture( File writeTo ) {
		setTexture(geometry, null, new int[] {9000, 3000} /*preview size */, pano.getRenderPano(), writeTo );
	}
	
	protected void setTexture(Geometry g, Material mat, int[] texSize, BufferedImage srcPano, File writeTo ) {
		
		if (!autoProject)
			return;
		
		if (cancel != null)
			cancel.cancel();
		
		cancel = new Cancellable();
		
		new Thread() {

			public void run() {

				VertexBuffer vb = g.getMesh().getBuffer(VertexBuffer.Type.TexCoord);
				VertexBuffer ib = g.getMesh().getBuffer(VertexBuffer.Type.Index);
				VertexBuffer pb = g.getMesh().getBuffer(VertexBuffer.Type.Position);

				float[][] pts = new float[3][3], uvs = new float[3][2];

				BufferedImage target = new BufferedImage(texSize[0], texSize[1], BufferedImage.TYPE_3BYTE_BGR);

				// if (false) {
				for (int i = 0; i < ib.getNumElements(); i++) {
					
//					System.out.println("rendering triangle " + i +"/ " + ib.getNumElements());
					
					assert ib.getNumComponents() == 3;

					for (int c = 0; c < ib.getNumComponents(); c++) {

						int ind = ((Number) ib.getElementComponent(i, c)).intValue();

						for (int x = 0; x < 3; x++)
							pts[c][x] = ((Number) pb.getElementComponent(ind, x)).floatValue();

						for (int u = 0; u < 2; u++)
							uvs[c][u] = ((Number) vb.getElementComponent(ind, u)).floatValue() * texSize[u];

					}

					cast(pts, uvs, srcPano, target, cancel, texSize);
					
					if (cancel.cancelled)
						return;
				}

//				File uniqueTexture = new File("/home/twak/Desktop/foo.png");

				try {
					System.out.println("writing " + writeTo);
					ImageIO.write(target, "png", writeTo);
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (mat != null)
				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {

						System.out.println("calculations complete");

						TextureKey key = new TextureKey("Desktop/foo.png", false);
						tweed.getAssetManager().deleteFromCache(key);
						Texture texture = tweed.getAssetManager().loadTexture(key);
						mat.setTexture("DiffuseMap", texture);
						return null;
					}
				});

			};
		}.start();
	}

	
	// https://fgiesen.wordpress.com/2013/02/08/triangle-rasterization-in-practice/
	// http://gamedev.stackexchange.com/questions/23743/whats-the-most-efficient-way-to-find-barycentric-coordinates
	private void cast(float[][] pts, float[][] uvs, BufferedImage srcPano, BufferedImage target, Cancellable cancel, int[] texSize) {

		int     minX = (int) Mathz.min(uvs[0][0], uvs[1][0], uvs[2][0]) - 1,
				maxX = (int) Math.ceil(Mathz.max(uvs[0][0], uvs[1][0], uvs[2][0])) + 1,
				minY = (int) Mathz.min(uvs[0][1], uvs[1][1], uvs[2][1]) -1,
				maxY = (int) Math.ceil(Mathz.max(uvs[0][1], uvs[1][1], uvs[2][1])) + 1;

		maxX = Mathz.clamp(maxX, 0, texSize[0] - 1);
		minX = Mathz.clamp(minX, 0, texSize[0] - 1);
		maxY = Mathz.clamp(maxY, 0, texSize[1] - 1);
		minY = Mathz.clamp(minY, 0, texSize[1] - 1);

		float[] uvs01 = Arrayz.sub(uvs[1], uvs[0]), 
				uvs02 = Arrayz.sub(uvs[2], uvs[0]);

		float padding = 2;
		
		float 
			sFac = -padding / distance (uvs[0], uvs[1], uvs[2]),
			tFac = -padding / distance (uvs[1], uvs[2], uvs[0]),
			uFac = -padding / distance (uvs[2], uvs[0], uvs[1]);
		
		float uv012 = cross(uvs01, uvs02);

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {

				float[] uvs0p = new float[] { x + 0.5f /* pixel center */ - uvs[0][0], y + 0.5f - uvs[0][1] };

				float u = cross(uvs01, uvs0p) / uv012, 
					  t = cross(uvs0p, uvs02) / uv012, 
					  s = 1 - u - t;
				
				if (s >= sFac && t >= tFac && u >= uFac ) {
					int c = pano.castTo(new float[] { 
							pts[0][0] * s + pts[1][0] * t + pts[2][0] * u,
							pts[0][1] * s + pts[1][1] * t + pts[2][1] * u,
							pts[0][2] * s + pts[1][2] * t + pts[2][2] * u,
																			}, srcPano, null, null);
					target.setRGB(x, y, c);
				}
				
				if (cancel.cancelled)
					return;
			}
		}
	}

	private static float distance(float[] p0, float[] p1, float[] p2) {
		
		return (float) (Math.abs ( (p2[0] - p1[0])*(p1[0] - p0[1]) - (p1[0] - p0[0]) * (p2[1] - p1[1]) ) /
			Math.sqrt( Math.pow ( p2[1] - p1[0], 2) + Math.pow (p2[1] - p1[1], 2) ) );
	}

	private static float cross(float[] a, float[] b) {
		return a[0] * b[1] - b[0] * a[1];
	}
}
