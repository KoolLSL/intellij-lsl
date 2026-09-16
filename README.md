![GitHub](https://img.shields.io/github/license/koollsl/intellij-lsl)


Write LSL (Linden Script Language) directly inside IntelliJ IDEA, PyCharm, Android Studio, and other JetBrains editors.

### Key Features

* **Project & File Organization:** Manage large multi-file projects using structured project views, shared library, local history, file comparison, GitHub integration, and more.
* **Advanced Preprocessor:** Use file inclusions (`#include`), function inlining (`#inline`), and conditional blocks
  (`#ifdef`...) to organize large script projects.
* **Memory Optimization:** Built-in constant optimization evaluates static math, replaces fixed variables, and eliminates dead code before compiling — keeping your script's memory footprint as small as possible in Second Life.
* **LSL Database:** Use the popular [kwdb.xml](https://github.com/Sei-Lisa/kwdb) from Sei-Lisa for the definition of functions, constants, and events. When new LSL functions are released, you can simply download or edit the XML file yourself without waiting for a plugin update!
* **Code Formatting & Clean Up:** Automatically format your code, fix indentation, and keep your scripts clean and readable.
* **Smart Scripting Tools:** Instant syntax highlighting, real-time error checking, smart auto-completion, and safe variable/function refactoring.

---
### How to Install

1. Open your JetBrains IDE such as [IntelliJ IDEA](https://www.jetbrains.com/idea/), PyCharm, Android Studio, or any
   other compatible IDE.
2. Go to **Settings** → **Plugins** (or **Preferences** → **Plugins** on macOS).
3. ~~Search for `Linden Script (LSL)` under the **Marketplace** tab~~, or click the **⚙️ icon** → **Install Plugin from
   Disk...** to use a downloaded `intellij-lsl.zip`
   from [GitHub Releases](https://github.com/KoolLSL/intellij-lsl/releases).
4. Click **Install** and restart your IDE if prompted.

### How to Use

1. **Create a Project:** Open your IDE, go to **File → New → Project...**, select **Linden Script (LSL)**, and click **Create**.
2. **Add Your Source File:**
    * **`.lslp` (Preprocessed File):** Create your main script file here (e.g., `MyScript.lslp`).
3. **Build:** Save your `.lslp` file (`Ctrl+S`). The plugin automatically generates an optimized, read-only **`.lsl`**
   script in the `/build` folder (e.g., `/build/MyScript.lsl`).
    * *Tip:* Right-click the `.lslp` editor tab and select **Open Generated .lsl** to quickly view the generated output
      in a new tab.
4. **Import into Second Life:** If you use the **Firestorm Viewer**, enable its LSL preprocessor to link directly to the
   generated `.lsl` file on your local disk when compiling in-world (e.g., using
   `#include "MyProject/build/MyScript.lsl"`). Alternatively, copy and paste the generated `.lsl` contents into your
   in-world script editor.

   *Tip: Type **LSL** in your IDE **Settings** to quickly access and customize plugin options.*

---

### Modules (Optional)

You can split your codebase into reusable files or use preprocessor directives as your project grows:

* **Module Files (`.lslm`):** Create helper files to store shared functions or constants (e.g., `MyLib.lslm`).
* **Including Modules:** Import your created `.lslm` files into any `.lslp` script using standard preprocessor syntax:
  (e.g., `#include "MyLib.lslm"`)
### Directives

* **`#define`** — Defines a constant or macro (e.g., `#define VERSION 3`).
* **`#undef`** — Removes an existing definition (e.g., `#undef DEBUG`).
* **`#if`** — Evaluates a custom boolean expression (e.g., `#if VERSION >= 2`).
* **`#ifdef`** — Includes code if an identifier is defined (e.g., `#ifdef DEBUG`).
* **`#ifndef`** — Includes code if an identifier is *not* defined (e.g., `#ifndef PROD`).
* **`#elif`** — Alternative conditional branch (`else if`) within a block.
* **`#else`** — Fallback branch when prior conditions fail.
* **`#endif`** — Closes an active conditional block.
* **`#include`** — Merges an external `.lslm` module (e.g., `#include "Vectors.lslm"`). The file must be in the same
  folder or in a folder of the Project properties / Content roots.
* **`#inline`** — Inlines the function written below directly in the code instead of calling it.
---

### Issues & Feedback

> This plugin is a personal side project and may not be 100% perfect. If you run into obvious issues, please report them
> on [GitHub Issues](https://github.com/KoolLSL/intellij-lsl/issues).

This project is a modernized fork of the original [riej/lsl](https://github.com/riej/lsl) plugin.

Compared to the Eclipse/LSLForge, this plugin has no internal simulator, but offers preprocessing and syntax checking
inside the more modern JetBrains IDEs. This also makes the plugin easier to install and maintain.

See [official LSL documentation](https://wiki.secondlife.com/wiki/LSL_Portal).


<sub style="color: #6a737d;">
Second Life (SL) and the Second Life Eye-in-Hand Logo are registered trademarks of Linden Research, Inc. This plugin is an independent third-party project and is not affiliated with, supported by, or endorsed by Linden Research, Inc.
</sub>
