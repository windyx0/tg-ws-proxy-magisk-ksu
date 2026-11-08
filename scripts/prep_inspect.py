import subprocess

code = """
try:
    from com.exteragram.messenger.plugins import Plugin, PluginsController
    import java.lang.reflect
    p_cls = Plugin.getClass()
    print("Plugin methods:")
    for m in p_cls.getDeclaredMethods():
        print("  ", m.getName(), m.getParameterTypes(), "->", m.getReturnType())
    print("Plugin fields:")
    for f in p_cls.getDeclaredFields():
        print("  ", f.getName(), f.getType())
except Exception as e:
    import traceback
    traceback.print_exc()
"""
with open("test_plugin_inspect.py", "w", encoding="utf-8") as f:
    f.write(code)

print("Created test_plugin_inspect.py")
