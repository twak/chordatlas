package org.twak.tweed.gen;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.terrain.geomipmap.TerrainPatch;
import com.jme3.terrain.geomipmap.TerrainQuad;


 /**
  * I come to life in my fast fast car
  * Your life can not ever be ours
  * My Testarossa same color as Mars
  * Buckle up, you are now riding with a star
  *
  */
public class TQ extends TerrainQuad{


    public TQ( String string, int blockSize, int split, Vector3f stepScale, float[] heightBlock1, int totalSize, Vector2f tempOffset, float offsetAmount ) {
    	super (string, blockSize, split, stepScale, heightBlock1, totalSize, tempOffset, offsetAmount );
	}

	public TQ( String string, int i, int size, float[] heightMap ) {
		super (string, i, size, heightMap);
	}

	protected void createQuadPatch(float[] heightMap) {
        // create 4 terrain patches
        int quarterSize = size >> 2;
        int halfSize = size >> 1;
        int split = (size + 1) >> 1;

        //if (lodCalculator == null)
        //    lodCalculator = createDefaultLodCalculator(); // set a default one

        offsetAmount += quarterSize;

        // 1 lower left
        float[] heightBlock1 = createHeightSubBlock(heightMap, 0, 0, split);

        Vector3f origin1 = new Vector3f(-halfSize * stepScale.x, 0, -halfSize
                        * stepScale.z);

        Vector2f tempOffset1 = new Vector2f();
        tempOffset1.x = offset.x;
        tempOffset1.y = offset.y;
        tempOffset1.x += origin1.x / 2;
        tempOffset1.y += origin1.z / 2;

        TerrainPatch patch1 = new TerrainPatch(getName() + "Patch1", split,
                        stepScale, heightBlock1, origin1, totalSize, tempOffset1,
                        offsetAmount);
        

        patch1.setQuadrant((short) 1);
        setMaterial(patch1);
        this.attachChild(patch1);
        patch1.setModelBound(new BoundingBox());
        patch1.updateModelBound();
        //patch1.setLodCalculator(lodCalculator);
        //TangentBinormalGenerator.generate(patch1);

        // 2 upper left
        float[] heightBlock2 = createHeightSubBlock(heightMap, 0, split - 1,
                        split);

        Vector3f origin2 = new Vector3f(-halfSize * stepScale.x, 0, 0);

        Vector2f tempOffset2 = new Vector2f();
        tempOffset2.x = offset.x;
        tempOffset2.y = offset.y;
        tempOffset2.x += origin1.x / 2;
        tempOffset2.y += quarterSize * stepScale.z;

        TerrainPatch patch2 = new TerrainPatch(getName() + "Patch2", split,
                        stepScale, heightBlock2, origin2, totalSize, tempOffset2,
                        offsetAmount);
        patch2.setQuadrant((short) 2);
        setMaterial(patch2);
        this.attachChild(patch2);
        patch2.setModelBound(new BoundingBox());
        patch2.updateModelBound();
        //patch2.setLodCalculator(lodCalculator);
        //TangentBinormalGenerator.generate(patch2);

        // 3 lower right
        float[] heightBlock3 = createHeightSubBlock(heightMap, split - 1, 0,
                        split);

        Vector3f origin3 = new Vector3f(0, 0, -halfSize * stepScale.z);

        Vector2f tempOffset3 = new Vector2f();
        tempOffset3.x = offset.x;
        tempOffset3.y = offset.y;
        tempOffset3.x += quarterSize * stepScale.x;
        tempOffset3.y += origin3.z / 2;

        TerrainPatch patch3 = new TerrainPatch(getName() + "Patch3", split,
                        stepScale, heightBlock3, origin3, totalSize, tempOffset3,
                        offsetAmount);
        patch3.setQuadrant((short) 3);
        setMaterial(patch3);
        this.attachChild(patch3);
        patch3.setModelBound(new BoundingBox());
        patch3.updateModelBound();
        //patch3.setLodCalculator(lodCalculator);
        //TangentBinormalGenerator.generate(patch3);

        // 4 upper right
        float[] heightBlock4 = createHeightSubBlock(heightMap, split - 1,
                        split - 1, split);

        Vector3f origin4 = new Vector3f(0, 0, 0);

        Vector2f tempOffset4 = new Vector2f();
        tempOffset4.x = offset.x;
        tempOffset4.y = offset.y;
        tempOffset4.x += quarterSize * stepScale.x;
        tempOffset4.y += quarterSize * stepScale.z;

        TerrainPatch patch4 = new TerrainPatch(getName() + "Patch4", split,
                        stepScale, heightBlock4, origin4, totalSize, tempOffset4,
                        offsetAmount);
        patch4.setQuadrant((short) 4);
        setMaterial(patch4);
        this.attachChild(patch4);
        patch4.setModelBound(new BoundingBox());
        patch4.updateModelBound();
        //patch4.setLodCalculator(lodCalculator);
        //TangentBinormalGenerator.generate(patch4);
    }

	private void setMaterial( TerrainPatch p ) {
		p.getMesh().setMode( Mesh.Mode.Lines );
	}
	
    protected void createQuad(int blockSize, float[] heightMap) {
        // create 4 terrain quads
        int quarterSize = size >> 2;

        int split = (size + 1) >> 1;

        Vector2f tempOffset = new Vector2f();
        offsetAmount += quarterSize;

        //if (lodCalculator == null)
        //    lodCalculator = createDefaultLodCalculator(); // set a default one

        // 1 upper left of heightmap, upper left quad
        float[] heightBlock1 = createHeightSubBlock(heightMap, 0, 0, split);

        Vector3f origin1 = new Vector3f(-quarterSize * stepScale.x, 0,
                        -quarterSize * stepScale.z);

        tempOffset.x = offset.x;
        tempOffset.y = offset.y;
        tempOffset.x += origin1.x;
        tempOffset.y += origin1.z;

        TerrainQuad quad1 = new TQ(getName() + "Quad1", blockSize,
                        split, stepScale, heightBlock1, totalSize, tempOffset,
                        offsetAmount);
        quad1.setLocalTranslation(origin1);
        quad1.setQuadrant( (short) 1 );
        this.attachChild(quad1);

        // 2 lower left of heightmap, lower left quad
        float[] heightBlock2 = createHeightSubBlock(heightMap, 0, split - 1,
                        split);

        Vector3f origin2 = new Vector3f(-quarterSize * stepScale.x, 0,
                        quarterSize * stepScale.z);

        tempOffset = new Vector2f();
        tempOffset.x = offset.x;
        tempOffset.y = offset.y;
        tempOffset.x += origin2.x;
        tempOffset.y += origin2.z;

        TerrainQuad quad2 = new TQ(getName() + "Quad2", blockSize,
                        split, stepScale, heightBlock2, totalSize, tempOffset,
                        offsetAmount);
        quad2.setLocalTranslation(origin2);
        quad2.setQuadrant( (short) 2 );
        this.attachChild(quad2);

        // 3 upper right of heightmap, upper right quad
        float[] heightBlock3 = createHeightSubBlock(heightMap, split - 1, 0,
                        split);

        Vector3f origin3 = new Vector3f(quarterSize * stepScale.x, 0,
                        -quarterSize * stepScale.z);

        tempOffset = new Vector2f();
        tempOffset.x = offset.x;
        tempOffset.y = offset.y;
        tempOffset.x += origin3.x;
        tempOffset.y += origin3.z;

        TerrainQuad quad3 = new TQ(getName() + "Quad3", blockSize,
                        split, stepScale, heightBlock3, totalSize, tempOffset,
                        offsetAmount);
        quad3.setLocalTranslation(origin3);
        quad3.setQuadrant( (short) 3 );
        this.attachChild(quad3);

        // 4 lower right of heightmap, lower right quad
        float[] heightBlock4 = createHeightSubBlock(heightMap, split - 1,
                        split - 1, split);

        Vector3f origin4 = new Vector3f(quarterSize * stepScale.x, 0,
                        quarterSize * stepScale.z);

        tempOffset = new Vector2f();
        tempOffset.x = offset.x;
        tempOffset.y = offset.y;
        tempOffset.x += origin4.x;
        tempOffset.y += origin4.z;

        TerrainQuad quad4 = new TQ(getName() + "Quad4", blockSize,
                        split, stepScale, heightBlock4, totalSize, tempOffset,
                        offsetAmount);
        quad4.setLocalTranslation(origin4);
        quad4.setQuadrant( (short) 4 );
        this.attachChild(quad4);

    }
	
}
