import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Structural porting check. Runtime tests are still required for injection points and local capture. */
public class MixinAudit {
    static Object value(AnnotationNode node, String key) {
        if (node.values != null) for (int i=0;i<node.values.size();i+=2)
            if (key.equals(node.values.get(i))) return node.values.get(i+1);
        return null;
    }
    static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        var list=new ArrayList<AnnotationNode>(); if(a!=null)list.addAll(a); if(b!=null)list.addAll(b); return list;
    }
    static ClassNode read(byte[] bytes) { var node=new ClassNode(); new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); return node; }
    public static void main(String[] args) throws Exception {
        var classes=new HashMap<String,ClassNode>();
        try(var jar=new JarFile(args[0])) {
            for(var entry:Collections.list(jar.entries())) if(entry.getName().endsWith(".class")) {
                var node=read(jar.getInputStream(entry).readAllBytes()); classes.put(node.name,node);
            }
        }
        try(var files=Files.walk(Path.of(args[1]))) {
            for(var path:files.filter(p->p.toString().endsWith(".class")).toList()) {
                var mixin=read(Files.readAllBytes(path));
                for(var ann:annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations)) {
                    if(!ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))continue;
                    var targets=new ArrayList<String>();
                    if(value(ann,"value") instanceof List<?> list) for(var type:list)targets.add(((Type)type).getInternalName());
                    if(value(ann,"targets") instanceof List<?> list) for(var type:list)targets.add(type.toString().replace('.','/'));
                    for(var name:targets) {
                        var target=classes.get(name); if(target==null)continue;
                        for(var method:mixin.methods) for(var injection:annotations(method.visibleAnnotations,method.invisibleAnnotations)) {
                            if(value(injection,"method") instanceof List<?> selectors) for(var item:selectors) {
                                var selector=item.toString(); if(selector.contains("*"))continue;
                                int split=selector.indexOf('('); String methodName=split<0?selector:selector.substring(0,split);
                                var matches=target.methods.stream().filter(m->m.name.equals(methodName)&&(split<0||m.desc.equals(selector.substring(split)))).toList();
                                if(matches.isEmpty()) System.out.println(mixin.name+" :: "+method.name+" MISSING "+name+"."+selector);
                                else if(injection.desc.endsWith("/Inject;")) {
                                    var params=Arrays.asList(Type.getArgumentTypes(method.desc));
                                    int callback=0;while(callback<params.size()&&!params.get(callback).getDescriptor().contains("/callback/CallbackInfo"))callback++;
                                    if(callback>0&&callback<params.size()) {
                                        var expected=params.subList(0,callback);
                                        if(matches.stream().noneMatch(m->Arrays.asList(Type.getArgumentTypes(m.desc)).equals(expected)))
                                            System.out.println(mixin.name+" :: "+method.name+" SIGNATURE "+method.desc+" TARGETS "+matches.stream().map(m->m.desc).toList());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
