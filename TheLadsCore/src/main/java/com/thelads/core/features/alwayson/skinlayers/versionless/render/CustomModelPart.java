package com.thelads.core.features.alwayson.skinlayers.versionless.render;

import com.thelads.core.features.alwayson.skinlayers.versionless.util.Vector3;
import java.util.List;

public abstract class CustomModelPart {
    protected float x;
    protected float y;
    protected float z;
    protected float xRot;
    protected float yRot;
    protected float zRot;
    protected boolean visible = true;
    protected float[] polygonData = null;
    protected int polygonAmount = 0;
    protected final int polyDataSize = 23;

    public CustomModelPart(List<CustomizableCube> customCubes) {
        this.compactCubes(customCubes);
    }

    private void compactCubes(List<CustomizableCube> customCubes) {
        for (CustomizableCube cube : customCubes) {
            this.polygonAmount += cube.polygonCount;
        }
        this.polygonData = new float[this.polygonAmount * 23];
        int offset = 0;
        for (CustomizableCube cube : customCubes) {
            for (int id = 0; id < cube.polygonCount; ++id) {
                CustomizableCube.Polygon polygon = cube.polygons[id];
                Vector3 vector3f = polygon.normal;
                this.polygonData[offset + 0] = vector3f.x;
                this.polygonData[offset + 1] = vector3f.y;
                this.polygonData[offset + 2] = vector3f.z;
                for (int i = 0; i < 4; ++i) {
                    CustomizableCube.Vertex vertex = polygon.vertices[i];
                    this.polygonData[offset + 3 + i * 5 + 0] = vertex.scaledX;
                    this.polygonData[offset + 3 + i * 5 + 1] = vertex.scaledY;
                    this.polygonData[offset + 3 + i * 5 + 2] = vertex.scaledZ;
                    this.polygonData[offset + 3 + i * 5 + 3] = vertex.u;
                    this.polygonData[offset + 3 + i * 5 + 4] = vertex.v;
                }
                offset += 23;
            }
        }
    }

    public void setPosition(float f, float g, float h) {
        this.x = f;
        this.y = g;
        this.z = h;
    }

    public void setRotation(float f, float g, float h) {
        this.xRot = f;
        this.yRot = g;
        this.zRot = h;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return this.visible;
    }
}
