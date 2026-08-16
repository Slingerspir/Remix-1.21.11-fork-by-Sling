package cn.remix.util.shader;

import cn.remix.util.shader.impl.RQShader;
import cn.remix.util.shader.impl.ROQShader;

public final class ShaderSystem {

    public static final RQShader RQ = new RQShader();
    public static final ROQShader ROQ = new ROQShader();

    private ShaderSystem() {}
}