# Keep showcase classes, annotations, methods, and fields indexed at build time so
# Reflections.collect(...) + getSubTypesOf / getTypesAnnotatedWith / getMethodsAnnotatedWith / getFieldsAnnotatedWith
# can resolve Class.forName(...) and reflection metadata under R8/ProGuard.
-keep @de.hartz.software.reflectionsdemo.showcase.DemoPlugin class * {
    <init>(...);
    @de.hartz.software.reflectionsdemo.showcase.DemoAction <methods>;
    @de.hartz.software.reflectionsdemo.showcase.DemoTag <fields>;
}
-keep class * implements de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature {
    <init>(...);
}
-keep interface de.hartz.software.reflectionsdemo.showcase.ShowcaseFeature { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoPlugin { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoAction { *; }
-keep @interface de.hartz.software.reflectionsdemo.showcase.DemoTag { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep JsonDB @Document entities and Jackson JSON serialization metadata under R8
-keep @io.jsondb.annotation.Document class * {
    <init>(...);
    <fields>;
    <methods>;
}
-keep @interface io.jsondb.annotation.Document { *; }
-keep @interface io.jsondb.annotation.Id { *; }
-keep @interface io.jsondb.annotation.Secret { *; }
-keep class io.jsondb.** { *; }
-dontwarn io.jsondb.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.**

# Keep Reflections store/serializer classes used by Gson/XML deserialization
-keep class org.reflections.** { *; }
-dontwarn org.reflections.**

# Optional compile/server-only dependencies referenced in Reflections & dom4j
-dontwarn javax.servlet.**
-dontwarn org.jboss.vfs.**
-dontwarn javassist.**
-dontwarn javax.annotation.**
-dontwarn org.jaxen.**
-dontwarn org.xmlpull.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.**
-dontwarn org.relaxng.**
-dontwarn org.slf4j.impl.**

