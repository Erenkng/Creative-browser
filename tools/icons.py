#!/usr/bin/env python3
"""Writes Oavi's icon set as vector drawables: 24dp, 1.8 stroke, round caps and joins."""
import os, sys

OUT = sys.argv[1]
CIRCLE = lambda cx, cy, r: f"M{cx},{cy}m-{r},0a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 -{2*r},0"

# name -> list of (pathData, kind) where kind is "stroke" or "fill"
ICONS = {
    "back": [("M19,12H5M11,6l-6,6 6,6", "stroke")],
    "forward": [("M5,12h14M13,6l6,6 -6,6", "stroke")],
    "reload": [("M19.5,12a7.5,7.5 0 1 1 -2.2,-5.3M17.6,3.2v3.9h-3.9", "stroke")],
    "close": [("M6,6l12,12M18,6L6,18", "stroke")],
    "plus": [("M12,5v14M5,12h14", "stroke")],
    "more": [(CIRCLE(5.5, 12, 1.6), "fill"), (CIRCLE(12, 12, 1.6), "fill"), (CIRCLE(18.5, 12, 1.6), "fill")],
    "search": [(CIRCLE(11, 11, 6.5), "stroke"), ("M16,16l4.5,4.5", "stroke")],
    "shield": [("M12,3l7,3v5.5c0,4.4 -2.9,7.8 -7,9.5c-4.1,-1.7 -7,-5.1 -7,-9.5V6z", "stroke"), ("M9,12l2.2,2.2L15.2,10", "stroke")],
    "shield_off": [("M12,3l7,3v5.5c0,4.4 -2.9,7.8 -7,9.5c-4.1,-1.7 -7,-5.1 -7,-9.5V6z", "stroke"), ("M4,4l16,16", "stroke")],
    "lock": [("M6.5,11h11a1.5,1.5 0 0 1 1.5,1.5v6a1.5,1.5 0 0 1 -1.5,1.5h-11a1.5,1.5 0 0 1 -1.5,-1.5v-6a1.5,1.5 0 0 1 1.5,-1.5z", "stroke"), ("M8.5,11V8a3.5,3.5 0 0 1 7,0v3", "stroke")],
    "warning": [("M12,4l9,15.5H3z", "stroke"), ("M12,10v4.2", "stroke"), (CIRCLE(12, 16.8, 0.9), "fill")],
    "share": [("M12,15V4M8,8l4,-4 4,4M5,13v5.5a1.5,1.5 0 0 0 1.5,1.5h11a1.5,1.5 0 0 0 1.5,-1.5V13", "stroke")],
    "bookmark": [("M7,4h10a1,1 0 0 1 1,1v15l-6,-4 -6,4V5a1,1 0 0 1 1,-1z", "stroke")],
    "bookmark_filled": [("M7,4h10a1,1 0 0 1 1,1v15l-6,-4 -6,4V5a1,1 0 0 1 1,-1z", "both")],
    "reader": [("M5,6h14M5,10h14M5,14h14M5,18h9", "stroke")],
    "moon": [("M20,14.5A8,8 0 0 1 9.5,4a8,8 0 1 0 10.5,10.5z", "stroke")],
    "sun": [(CIRCLE(12, 12, 4), "stroke"), ("M12,2.5v2M12,19.5v2M2.5,12h2M19.5,12h2M5.3,5.3l1.4,1.4M17.3,17.3l1.4,1.4M5.3,18.7l1.4,-1.4M17.3,6.7l1.4,-1.4", "stroke")],
    "find": [("M4,6h9M4,10h5", "stroke"), (CIRCLE(14.5, 14.5, 4), "stroke"), ("M17.5,17.5l3,3", "stroke")],
    "history": [(CIRCLE(12, 12, 8.5), "stroke"), ("M12,7.5V12l3,2", "stroke")],
    "download": [("M12,4v11M8,11l4,4 4,-4M5,19.5h14", "stroke")],
    "settings": [("M4,7h9M19,7h1M4,17h1M11,17h9", "stroke"), (CIRCLE(16, 7, 2.2), "stroke"), (CIRCLE(8, 17, 2.2), "stroke")],
    "private_mode": [("M3,9.5h18M7,9.5l1.5,-5h7l1.5,5", "stroke"), (CIRCLE(7.5, 15, 3), "stroke"), (CIRCLE(16.5, 15, 3), "stroke"), ("M10.5,15a1.5,1.5 0 0 1 3,0", "stroke")],
    "desktop": [("M4,5h16a1,1 0 0 1 1,1v9a1,1 0 0 1 -1,1H4a1,1 0 0 1 -1,-1V6a1,1 0 0 1 1,-1zM9,20h6M12,16v4", "stroke")],
    "copy": [("M9,9h10a1,1 0 0 1 1,1v10a1,1 0 0 1 -1,1H9a1,1 0 0 1 -1,-1V10a1,1 0 0 1 1,-1z", "stroke"), ("M5,15H4a1,1 0 0 1 -1,-1V4a1,1 0 0 1 1,-1h10a1,1 0 0 1 1,1v1", "stroke")],
    "globe": [(CIRCLE(12, 12, 8.5), "stroke"), ("M3.5,12h17M12,3.5c3,3 3,14 0,17M12,3.5c-3,3 -3,14 0,17", "stroke")],
    "archive": [("M4,5h16v4H4zM5.5,9v9a1.5,1.5 0 0 0 1.5,1.5h10a1.5,1.5 0 0 0 1.5,-1.5V9M10,13h4", "stroke")],
    "check": [("M5,12.5l4.5,4.5L19,7.5", "stroke")],
    "chevron_right": [("M9,6l6,6 -6,6", "stroke")],
    "chevron_down": [("M6,9l6,6 6,-6", "stroke")],
    "chevron_up": [("M6,15l6,-6 6,6", "stroke")],
    "group": [("M12,4l8,4 -8,4 -8,-4zM4,12l8,4 8,-4M4,16l8,4 8,-4", "stroke")],
    "trash": [("M5,7h14M10,7V5h4v2M7,7l1,12.5a1.5,1.5 0 0 0 1.5,1.5h5a1.5,1.5 0 0 0 1.5,-1.5L17,7", "stroke")],
    "open_new": [("M14,5h5v5M19,5l-8,8M17,14v4.5a1.5,1.5 0 0 1 -1.5,1.5h-10A1.5,1.5 0 0 1 4,18.5v-10A1.5,1.5 0 0 1 5.5,7H10", "stroke")],
    "image": [("M5,5h14a1,1 0 0 1 1,1v12a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V6a1,1 0 0 1 1,-1zM4,16l4.5,-4.5 4,4 2.5,-2.5 5,5", "stroke"), (CIRCLE(15.5, 9.5, 1.5), "stroke")],
    "bolt": [("M13,3L5,14h6l-1,7 8,-11h-6z", "stroke")],
    "undo": [("M4,10h10a5,5 0 0 1 0,10h-3M8,6l-4,4 4,4", "stroke")],
    "link": [("M10,14a4,4 0 0 0 5.7,0l3,-3a4,4 0 0 0 -5.7,-5.7l-1,1M14,10a4,4 0 0 0 -5.7,0l-3,3a4,4 0 0 0 5.7,5.7l1,-1", "stroke")],
    "edit": [("M4,20h4L19,9l-4,-4L4,16zM13,7l4,4", "stroke")],
    "text_size": [("M3,18L8,6l5,12M4.7,14h6.6M14.5,18l3,-7.5 3,7.5M15.5,15.7h4", "stroke")],
    "tab": [("M7,4h10a3,3 0 0 1 3,3v10a3,3 0 0 1 -3,3H7a3,3 0 0 1 -3,-3V7a3,3 0 0 1 3,-3z", "stroke")],
    "home": [("M4,11l8,-7 8,7M6,9.5V19a1,1 0 0 0 1,1h10a1,1 0 0 0 1,-1V9.5", "stroke")],
}

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths}</vector>
"""

def path(data, kind):
    if kind == "fill":
        return f'    <path\n        android:fillColor="#FF000000"\n        android:pathData="{data}" />\n'
    fill = "#FF000000" if kind == "both" else "#00000000"
    return (f'    <path\n        android:fillColor="{fill}"\n        android:pathData="{data}"\n'
            '        android:strokeColor="#FF000000"\n        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round"\n        android:strokeWidth="1.8" />\n')

os.makedirs(OUT, exist_ok=True)
for name, paths in ICONS.items():
    with open(os.path.join(OUT, f"ic_{name}.xml"), "w") as f:
        f.write(TEMPLATE.format(paths="".join(path(d, k) for d, k in paths)))
print(len(ICONS), "icons")
