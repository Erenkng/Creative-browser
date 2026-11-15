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
    "mic": [("M12,3.5a3,3 0 0 1 3,3v5a3,3 0 0 1 -6,0v-5a3,3 0 0 1 3,-3zM6,11a6,6 0 0 0 12,0M12,17v3.5M9,20.5h6", "stroke")],
    "translate": [("M4,5h9M8.5,3v2M6,5c0.8,3.2 3,5.6 6,7M11,5c-0.8,3.6 -3.2,6.4 -6.5,8M12.5,21l4,-9 4,9M14,17.5h5", "stroke")],
    "home": [("M4,11l8,-7 8,7M6,9.5V19a1,1 0 0 0 1,1h10a1,1 0 0 0 1,-1V9.5", "stroke")],
    "close_others": [("M5.5,8h9a1.5,1.5 0 0 1 1.5,1.5v9a1.5,1.5 0 0 1 -1.5,1.5h-9a1.5,1.5 0 0 1 -1.5,-1.5v-9a1.5,1.5 0 0 1 1.5,-1.5zM8,8V5.5A1.5,1.5 0 0 1 9.5,4h9A1.5,1.5 0 0 1 20,5.5v9a1.5,1.5 0 0 1 -1.5,1.5H16M7.5,11.5l5,5M12.5,11.5l-5,5", "stroke")],
    "swipe": [("M4,12h16M8,8l-4,4 4,4M16,8l4,4 -4,4", "stroke")],
    "sidebar": [("M5,4h14a1,1 0 0 1 1,1v14a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V5a1,1 0 0 1 1,-1zM9.5,4v16M6.2,8h1.2M6.2,11h1.2", "stroke")],
    "save_page": [("M7,3h7l5,5v12a1,1 0 0 1 -1,1H7a1,1 0 0 1 -1,-1V4a1,1 0 0 1 1,-1zM14,3v5h5M12.5,11v6M10,14.5l2.5,2.5 2.5,-2.5", "stroke")],
    "print": [("M7,9V4h10v5M7,17H5a1,1 0 0 1 -1,-1v-5a2,2 0 0 1 2,-2h12a2,2 0 0 1 2,2v5a1,1 0 0 1 -1,1h-2M7,14h10v6H7z", "stroke")],
    "speak": [("M4,9.5h3l4.5,-4v13l-4.5,-4H4zM15,9a4,4 0 0 1 0,6M17.5,6.5a7.5,7.5 0 0 1 0,11", "stroke")],
    "pause": [("M9,6v12M15,6v12", "stroke")],
    "play": [("M8,5.5v13l10,-6.5z", "stroke")],
    "qr": [("M4,4h6v6H4zM14,4h6v6h-6zM4,14h6v6H4zM14,14h2.5v2.5H14zM17.5,17.5H20V20h-2.5zM20,14v1.5M14,20v-1", "stroke")],
    "paste": [("M9,3.5h6v3H9zM9,5H6.5a1,1 0 0 0 -1,1v13.5a1,1 0 0 0 1,1h11a1,1 0 0 0 1,-1V6a1,1 0 0 0 -1,-1H15M9,12h6M9,15.5h4", "stroke")],
    "cookie": [("M20,12.5A8,8 0 1 1 11.5,4a3,3 0 0 0 3.5,3.5a3,3 0 0 0 5,5z", "stroke"), (CIRCLE(9, 10, 1), "fill"), (CIRCLE(14, 15, 1), "fill"), (CIRCLE(9, 15.5, 1), "fill")],
    "image_off": [("M5,5h14a1,1 0 0 1 1,1v12a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V6a1,1 0 0 1 1,-1zM4,16l4.5,-4.5 4,4 2.5,-2.5 5,5M3,3l18,18", "stroke")],
    "minus": [("M5,12h14", "stroke")],
    "palette": [("M12,3.5a8.5,8.5 0 1 0 0,17c1.2,0 1.8,-0.8 1.8,-1.7c0,-1.4 -1.3,-1.6 -1.3,-2.8c0,-1 0.8,-1.6 1.8,-1.6h2.2a4,4 0 0 0 4,-4c0,-3.8 -3.8,-6.9 -8.5,-6.9z", "stroke"), (CIRCLE(8, 10.5, 1.1), "fill"), (CIRCLE(11, 7.2, 1.1), "fill"), (CIRCLE(15.3, 8, 1.1), "fill")],
    "mirror": [("M12,3v18M8,7l-4,5 4,5M16,7l4,5 -4,5", "stroke")],
    "layout": [("M5,4h14a1,1 0 0 1 1,1v14a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V5a1,1 0 0 1 1,-1zM4,9h16M10,9v11", "stroke")],
    "dns": [(CIRCLE(12, 12, 8.5), "stroke"), ("M3.5,12h17M12,3.5c3,3 3,14 0,17M12,3.5c-3,3 -3,14 0,17", "stroke")],
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
