package org.twak.readTrace;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.vecmath.Tuple2d;
import javax.vecmath.Tuple3d;

import org.twak.utils.BitTwiddle;
import org.twak.utils.ObjDump;
import org.twak.utils.ObjDump.Face;
import org.twak.utils.ObjDump.Material;


/**
 * Takes a mesh with multiple materials,
 * and returns a mesh with a single (big)
 * material. For google textures only.
 * @author twak
 *
 */
public class TextureAtlas {
	
	public ObjDump done;
	
	private final static String ATLAS_FILENAME = "atlas.png";
	
	public TextureAtlas (File dir, ObjDump in) {
		
		List<Material> mats = new ArrayList ( in.material2Face.keySet() );
		Collections.sort ( mats, new Comparator<Material> () {
			@Override
			public int compare(Material o1, Material o2) {
				
				int o = Integer.compare ( o2.w, o1.w);
				if (o != 0)
					return o;
				return
					Integer.compare (o2.h, o1.h);
			}
		} );
		long area = (long) mats.stream().mapToDouble(x -> x.h * x.w).sum();

		List<int[]> newCoords = new ArrayList<>();
		
		int 	maxHeight = mats.get(0).h, 
				tileWidth = mats.get(0).w,
				x = 0, y = 0, tileYI = 0,
				maxRowWidth = BitTwiddle.nextPowerOf2( (int) Math.ceil ( Math.sqrt(area) / 1024) * 1024 );
//				maxRowWidth = (int) Math.ceil ( Math.sqrt(area) / 1024) * 1024;

		/**
		 * in a tile we stack upto maxHeight, then on by tileWidth. When we pass maxRowWidth, we increment tileYI,
		 * and add another row of tiles
		 */
		
		if (maxHeight > 1024)
			throw new Error("unexpected tile height! " + maxHeight);
		
		for (Material m : mats) {
			
			if (x + m.w > maxRowWidth ) {
				
				tileYI++;
				x = 0;
				y = tileYI * maxHeight;
				tileWidth = m.w;
			}
			
			if (tileWidth != 0 && m.w != tileWidth) {
				y = tileYI * maxHeight;
				x += tileWidth;
			}
			
			tileWidth = m.w;
			
			newCoords.add(new int[]{x,y});
			
			y += m.h;
			
			if ( y >= maxHeight) {
				y = tileYI*maxHeight;
				x+= m.w;
				tileWidth = 0;
			}
		}
		
		
		int aWidth = maxRowWidth, aHeight = ( tileYI + (y == 0 ? 0 :1 ) ) * maxHeight;
		
		BufferedImage atlas = new BufferedImage( aWidth, aHeight, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g =  (Graphics2D) atlas.getGraphics();
		
		for (int i = 0; i < mats.size(); i++) {
			
			Material mat = mats.get(i);
			int[] coords = newCoords.get(i);
			
			try {
				/** g has origin at top left, uvs at bottom left **/
				g.drawImage(ImageIO.read(new File (dir, mat.filename ) ), coords[0], aHeight - mat.h - coords[1], null);
			} catch (IOException e) {
				System.err.println("failed to read "+ mat.filename);
				e.printStackTrace();
			}
		}
		
		try {
			ImageIO.write(atlas, "png", new File(dir, ATLAS_FILENAME));
		} catch (IOException e) {
			e.printStackTrace();
		}

		done = new ObjDump();
		done.setCurrentTexture(ATLAS_FILENAME, aWidth, aHeight);

		for (int mi = 0; mi < mats.size(); mi++) {

			Material m = mats.get(mi);
			int[] offset = newCoords.get(mi);

			for (Face f : in.material2Face.get(m)) {

				int count = f.vtIndexes.size();
				float[][] pts = new float[count][3], uvs = new float[count][2];

				for (int i = 0; i < count; i++) {
					Tuple3d pt = in.orderVert.get(f.vtIndexes.get(i));
					pts[i][0] = (float) pt.x;
					pts[i][1] = (float) pt.y;
					pts[i][2] = (float) pt.z;

					Tuple2d uv = in.orderUV.get(f.uvIndexes.get(i));
					uvs[i][0] = (float) ((uv.x * m.w) + offset[0]) / aWidth;
					uvs[i][1] = (float) ((uv.y * m.h) + offset[1]) / aHeight;
				}

				done.addFace(pts, uvs, null);
			}
		}
	
		
	}
}
