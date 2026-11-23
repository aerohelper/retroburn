// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

// RetroBurn's Windows helper: drives the IMAPI2 COM objects for the Java application.
//
// Compiled at start by retroburn-helper.ps1 with Add-Type, so it must stay within C# 5 (no string
// interpolation, no ?. operator, no expression-bodied members). Reads one JSON request from stdin
// and writes JSON Lines to stdout; see docs/design/LLD-windows-platform.md section 4.
//
// Every IMAPI2 constant below was read from the type libraries with
// tools/windows/dump-imapi2-typelib.ps1; the name next to each value is the one printed there.

using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using ComTypes = System.Runtime.InteropServices.ComTypes;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using Microsoft.Win32.SafeHandles;

namespace RetroBurn.Helper
{
    public static class Program
    {
        const int ProtocolVersion = 1;

        /// <summary>Runs one request; returns the process exit code (0 success, 1 failure).</summary>
        public static int Run(string powerShellVersion, string languageMode)
        {
            Output output = new Output();
            try
            {
                Input input = Input.Open();
                Request request = input.ReadRequest();
                if (request.Int("protocol") != ProtocolVersion)
                {
                    throw new HelperError("BAD_REQUEST", "Unsupported protocol version " + request.Int("protocol"));
                }
                input.WatchForClose();
                Operations operations = new Operations(output, input);
                string operation = request.Text("operation");
                switch (operation)
                {
                    case "probe":
                        output.Emit("ready", "powershell", powerShellVersion, "clr", Environment.Version.ToString(),
                            "languageMode", languageMode);
                        break;
                    case "list-drives": operations.ListDrives(); break;
                    case "read-media": operations.ReadMedia(request.Text("drive")); break;
                    case "build-image": operations.BuildImage(request); break;
                    case "burn-image":
                        operations.BurnImage(request.Text("drive"), request.Text("image"), request.Bool("closeMedia"));
                        break;
                    case "blank": operations.Blank(request.Text("drive"), request.Bool("full")); break;
                    case "verify-image": operations.VerifyImage(request.Text("drive"), request.Text("image")); break;
                    case "eject": operations.Eject(request.Text("drive")); break;
                    case "watch-drives": operations.WatchDrives(); break;
                    case "keep-awake": operations.KeepAwake(request.Text("reason")); break;
                    case "power": operations.Power(); break;
                    default: throw new HelperError("BAD_REQUEST", "Unknown operation: " + operation);
                }
                output.Emit("result");
                return 0;
            }
            catch (Exception e)
            {
                Exception cause = Com.Unwrap(e);
                HelperError helperError = cause as HelperError;
                COMException comError = cause as COMException;
                if (helperError != null)
                {
                    output.Error(helperError.Code, helperError.Message, helperError.ComErrorCode);
                }
                else if (comError != null)
                {
                    output.Error("IMAPI_ERROR", comError.Message.Trim(), comError.ErrorCode);
                }
                else
                {
                    output.Error("IMAPI_ERROR", cause.GetType().Name + ": " + cause.Message, null);
                }
                return 1;
            }
        }
    }

    /// <summary>A failure with one of the protocol's error codes.</summary>
    sealed class HelperError : Exception
    {
        public readonly string Code;
        public readonly int? ComErrorCode;

        public HelperError(string code, string message) : base(message)
        {
            Code = code;
        }

        public HelperError(string code, string message, int comErrorCode) : base(message)
        {
            Code = code;
            ComErrorCode = comErrorCode;
        }
    }

    /// <summary>Writes JSON Lines to stdout, from any thread.</summary>
    sealed class Output
    {
        readonly object gate = new object();
        readonly StreamWriter writer;
        readonly JavaScriptSerializer json = new JavaScriptSerializer();

        public Output()
        {
            // The raw stream, not PowerShell's host, so the encoding is UTF-8 without BOM.
            writer = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false));
            writer.NewLine = "\n";
            writer.AutoFlush = true;
        }

        /// <summary>Writes one event; fields are name/value pairs.</summary>
        public void Emit(string type, params object[] fields)
        {
            Dictionary<string, object> line = new Dictionary<string, object>();
            line["event"] = type;
            for (int i = 0; i + 1 < fields.Length; i += 2)
            {
                line[(string)fields[i]] = fields[i + 1];
            }
            string text = json.Serialize(line);
            lock (gate)
            {
                writer.WriteLine(text);
            }
        }

        public void Log(string text)
        {
            Emit("log", "text", text);
        }

        public void Error(string code, string message, int? comErrorCode)
        {
            if (comErrorCode.HasValue)
            {
                Emit("error", "code", code, "message", message, "hresult", "0x" + comErrorCode.Value.ToString("X8"));
            }
            else
            {
                Emit("error", "code", code, "message", message);
            }
        }
    }

    /// <summary>Reads the request, then notices when Java closes stdin (which means "stop").</summary>
    sealed class Input
    {
        readonly StreamReader reader;
        readonly ManualResetEvent closed = new ManualResetEvent(false);
        readonly object gate = new object();
        Action onClose;

        Input(StreamReader reader)
        {
            this.reader = reader;
        }

        public static Input Open()
        {
            return new Input(new StreamReader(Console.OpenStandardInput(), new UTF8Encoding(false)));
        }

        public Request ReadRequest()
        {
            string line = reader.ReadLine();
            if (line == null)
            {
                throw new HelperError("BAD_REQUEST", "No request on stdin");
            }
            JavaScriptSerializer json = new JavaScriptSerializer();
            json.MaxJsonLength = int.MaxValue;
            Dictionary<string, object> values;
            try
            {
                values = json.Deserialize<Dictionary<string, object>>(line);
            }
            catch (ArgumentException e)
            {
                throw new HelperError("BAD_REQUEST", "Request is not valid JSON: " + e.Message);
            }
            return new Request(values);
        }

        /// <summary>Starts a background thread that waits for the end of stdin.</summary>
        public void WatchForClose()
        {
            Thread thread = new Thread(() =>
            {
                try
                {
                    while (reader.ReadLine() != null)
                    {
                        // Lines after the request carry no meaning in protocol version 1.
                    }
                }
                catch (IOException)
                {
                    // A broken pipe means Java is gone: treat it as closed.
                }
                Action action;
                lock (gate)
                {
                    closed.Set();
                    action = onClose;
                }
                if (action != null)
                {
                    action();
                }
            });
            thread.IsBackground = true;
            thread.Start();
        }

        public bool IsClosed
        {
            get { return closed.WaitOne(0); }
        }

        /// <summary>Waits until stdin closes or the timeout passes; true if it closed.</summary>
        public bool WaitForClose(int milliseconds)
        {
            return closed.WaitOne(milliseconds);
        }

        /// <summary>Runs the action when stdin closes (at once if it already has).</summary>
        public void OnClose(Action action)
        {
            bool alreadyClosed;
            lock (gate)
            {
                onClose = action;
                alreadyClosed = closed.WaitOne(0);
            }
            if (alreadyClosed)
            {
                action();
            }
        }

        public void ThrowIfClosed()
        {
            if (IsClosed)
            {
                throw new HelperError("CANCELLED", "Stopped on request");
            }
        }
    }

    /// <summary>The request's fields, with checks that name the missing field.</summary>
    sealed class Request
    {
        readonly Dictionary<string, object> values;

        public Request(Dictionary<string, object> values)
        {
            this.values = values;
        }

        object Value(string name)
        {
            object value;
            if (!values.TryGetValue(name, out value) || value == null)
            {
                throw new HelperError("BAD_REQUEST", "Missing field: " + name);
            }
            return value;
        }

        public string Text(string name)
        {
            string value = Value(name) as string;
            if (value == null)
            {
                throw new HelperError("BAD_REQUEST", "Field is not text: " + name);
            }
            return value;
        }

        public int Int(string name)
        {
            object value = Value(name);
            if (!(value is int))
            {
                throw new HelperError("BAD_REQUEST", "Field is not a whole number: " + name);
            }
            return (int)value;
        }

        public bool Bool(string name)
        {
            object value = Value(name);
            if (!(value is bool))
            {
                throw new HelperError("BAD_REQUEST", "Field is not true or false: " + name);
            }
            return (bool)value;
        }

        public ArrayList List(string name)
        {
            ArrayList value = Value(name) as ArrayList;
            if (value == null)
            {
                throw new HelperError("BAD_REQUEST", "Field is not a list: " + name);
            }
            return value;
        }
    }

    sealed class Operations
    {
        const int SectorSize = 2048;
        const int ProgressIntervalMilliseconds = 250;

        // HRESULT "There is no media in the device." (observed with the tray open).
        const int NoMediaHResult = unchecked((int)0xC0AA0202);

        // FsiFileSystems: FsiFileSystemISO9660 | FsiFileSystemJoliet | FsiFileSystemUDF.
        const int IsoJolietUdf = 0x1 | 0x2 | 0x4;

        // FsiItemType returned by IFileSystemImage.Exists: 0 means not found.
        const int FsiItemNotFound = 0;

        static readonly string[] WriteStages =
        {
            "validating",   // IMAPI_FORMAT2_DATA_WRITE_ACTION_VALIDATING_MEDIA = 0x0
            "formatting",   // IMAPI_FORMAT2_DATA_WRITE_ACTION_FORMATTING_MEDIA = 0x1
            "initialising", // IMAPI_FORMAT2_DATA_WRITE_ACTION_INITIALIZING_HARDWARE = 0x2
            "calibrating",  // IMAPI_FORMAT2_DATA_WRITE_ACTION_CALIBRATING_POWER = 0x3
            "writing",      // IMAPI_FORMAT2_DATA_WRITE_ACTION_WRITING_DATA = 0x4
            "finalising",   // IMAPI_FORMAT2_DATA_WRITE_ACTION_FINALIZATION = 0x5
            "completed",    // IMAPI_FORMAT2_DATA_WRITE_ACTION_COMPLETED = 0x6
            "verifying",    // IMAPI_FORMAT2_DATA_WRITE_ACTION_VERIFYING = 0x7
        };

        readonly Output output;
        readonly Input input;

        public Operations(Output output, Input input)
        {
            this.output = output;
            this.input = input;
        }

        public void ListDrives()
        {
            foreach (string id in Imapi.DriveIds())
            {
                dynamic recorder;
                try
                {
                    recorder = Imapi.Recorder(id);
                }
                catch (COMException e)
                {
                    output.Log("Drive " + id + " could not be opened: " + e.Message.Trim());
                    output.Emit("drive", "id", id, "vendor", "", "product", "", "revision", "", "volume", "",
                        "bus", "unknown", "accessible", false);
                    continue;
                }
                string volume = Imapi.Volume(recorder);
                output.Emit("drive", "id", id, "vendor", (string)recorder.VendorId, "product", (string)recorder.ProductId,
                    "revision", (string)recorder.ProductRevision, "volume", volume, "bus", Storage.Bus(volume),
                    "accessible", true);
            }
        }

        public void ReadMedia(string drive)
        {
            dynamic recorder = Imapi.FindRecorder(drive);
            dynamic data = Imapi.DataWriter(recorder);
            int physicalType;
            int status;
            try
            {
                physicalType = (int)data.CurrentPhysicalMediaType;
                status = (int)data.CurrentMediaStatus;
            }
            catch (COMException e)
            {
                if (e.ErrorCode == NoMediaHResult)
                {
                    output.Emit("media", "present", false);
                    return;
                }
                throw;
            }
            output.Emit("media", "present", true, "physicalType", physicalType, "status", status,
                "freeSectors", SectorsOrZero(data, "FreeSectorsOnMedia"),
                "totalSectors", SectorsOrZero(data, "TotalSectorsOnMedia"));
        }

        long SectorsOrZero(object data, string property)
        {
            try
            {
                return Convert.ToInt64(Com.Get(data, property));
            }
            catch (Exception e)
            {
                output.Log(property + " is not available: " + Com.Unwrap(e).Message.Trim());
                return 0;
            }
        }

        public void BuildImage(Request request)
        {
            string target = request.Text("target");
            dynamic image = Com.Create("IMAPI2FS.MsftFileSystemImage");
            image.FileSystemsToCreate = IsoJolietUdf;
            image.FreeMediaBlocks = 0; // 0 means no size limit (the default is a 650 MB CD).
            image.VolumeName = request.Text("volumeLabel");
            dynamic root = image.Root;
            foreach (object item in request.List("entries"))
            {
                input.ThrowIfClosed();
                Request entry = new Request((Dictionary<string, object>)item);
                AddEntry(image, root, entry.Text("discPath"), entry.Text("source"));
            }
            dynamic result = image.CreateResultImage();
            ComTypes.IStream stream = (ComTypes.IStream)result.ImageStream;
            CopyToFile(stream, target);
        }

        void AddEntry(dynamic image, dynamic root, string discPath, string source)
        {
            // Disc paths look like /Photos/2026; IMAPI2FS wants Photos\2026, relative to the root.
            string path = discPath.TrimStart('/').Replace('/', '\\');
            int lastSeparator = path.LastIndexOf('\\');
            EnsureDirectory(image, root, lastSeparator < 0 ? "" : path.Substring(0, lastSeparator));
            if (Directory.Exists(source))
            {
                EnsureDirectory(image, root, path);
                dynamic folder = Com.Get(root, "Item", path);
                folder.AddTree(source, false);
            }
            else if (File.Exists(source))
            {
                root.AddFile(path, Streams.OpenRead(source));
            }
            else
            {
                // The message is just the path, so RetroBurn can show it in a translated sentence.
                throw new HelperError("SOURCE_MISSING", source);
            }
        }

        static void EnsureDirectory(dynamic image, dynamic root, string path)
        {
            if (path.Length == 0)
            {
                return;
            }
            // Parents first: AddDirectory fails when the parent is missing.
            string[] segments = path.Split('\\');
            string prefix = "";
            foreach (string segment in segments)
            {
                prefix = prefix.Length == 0 ? segment : prefix + "\\" + segment;
                if ((int)image.Exists("\\" + prefix) == FsiItemNotFound)
                {
                    root.AddDirectory(prefix);
                }
            }
        }

        void CopyToFile(ComTypes.IStream stream, string target)
        {
            ComTypes.STATSTG stat;
            stream.Stat(out stat, 1 /* STATFLAG_NONAME */);
            long total = stat.cbSize;
            if (File.Exists(target))
            {
                File.Delete(target);
            }
            bool complete = false;
            IntPtr readCount = Marshal.AllocHGlobal(sizeof(int));
            try
            {
                using (FileStream file = new FileStream(target, FileMode.CreateNew, FileAccess.Write))
                {
                    byte[] buffer = new byte[1024 * 1024];
                    long done = 0;
                    Throttle throttle = new Throttle(ProgressIntervalMilliseconds);
                    output.Emit("progress", "stage", "image", "done", 0L, "total", total);
                    while (true)
                    {
                        input.ThrowIfClosed();
                        stream.Read(buffer, buffer.Length, readCount);
                        int read = Marshal.ReadInt32(readCount);
                        if (read <= 0)
                        {
                            break;
                        }
                        file.Write(buffer, 0, read);
                        done += read;
                        if (throttle.Due())
                        {
                            output.Emit("progress", "stage", "image", "done", done, "total", total);
                        }
                    }
                    output.Emit("progress", "stage", "image", "done", done, "total", total);
                }
                complete = true;
            }
            finally
            {
                Marshal.FreeHGlobal(readCount);
                if (!complete)
                {
                    TryDelete(target);
                }
            }
        }

        void TryDelete(string file)
        {
            try
            {
                File.Delete(file);
            }
            catch (IOException e)
            {
                output.Log("Could not delete " + file + ": " + e.Message);
            }
            catch (UnauthorizedAccessException e)
            {
                output.Log("Could not delete " + file + ": " + e.Message);
            }
        }

        public void BurnImage(string drive, string imagePath, bool closeMedia)
        {
            dynamic recorder = Imapi.FindRecorder(drive);
            dynamic data = Imapi.DataWriter(recorder);
            RequireMedia(data, recorder);
            long imageSectors = (new FileInfo(imagePath).Length + SectorSize - 1) / SectorSize;
            long freeSectors = Convert.ToInt64(data.FreeSectorsOnMedia);
            if (imageSectors > freeSectors)
            {
                throw new HelperError("NOT_ENOUGH_SPACE",
                    "The image needs " + imageSectors + " sectors; the disc has " + freeSectors + " free");
            }
            data.ForceMediaToBeClosed = closeMedia;
            // A written DVD+RW, DVD-RAM or BD-RE is overwritten, as the engine contract says; without
            // this IMAPI2 refuses with 0xC0AA0405 (verified). The UI asks the user before burning.
            data.ForceOverwrite = true;
            try
            {
                recorder.AcquireExclusiveAccess(true, "RetroBurn");
            }
            catch (COMException e)
            {
                throw new HelperError("DRIVE_BUSY", e.Message.Trim(), e.ErrorCode);
            }
            bool cancelled = false;
            DataSink sink = new DataSink(progress => ReportWrite(progress));
            Events.Connection connection = Events.Connect(data, Events.DataEvents, sink);
            try
            {
                // Closing stdin asks IMAPI2 to stop; the objects are free-threaded (-Mta), so the
                // reader thread may call CancelWrite while Write blocks this one.
                input.OnClose(() =>
                {
                    cancelled = true;
                    try
                    {
                        data.CancelWrite();
                    }
                    catch (Exception e)
                    {
                        output.Log("CancelWrite failed: " + Com.Unwrap(e).Message.Trim());
                    }
                });
                data.Write(Streams.OpenRead(imagePath));
            }
            catch (COMException e)
            {
                if (cancelled)
                {
                    throw new HelperError("CANCELLED", e.Message.Trim(), e.ErrorCode);
                }
                throw;
            }
            finally
            {
                input.OnClose(() => { });
                connection.Dispose();
                recorder.ReleaseExclusiveAccess();
            }
            if (cancelled)
            {
                throw new HelperError("CANCELLED", "Stopped on request");
            }
        }

        void ReportWrite(dynamic progress)
        {
            int action = (int)progress.CurrentAction;
            string stage = action >= 0 && action < WriteStages.Length ? WriteStages[action] : "action-" + action;
            long done = (long)(int)progress.LastWrittenLba - (int)progress.StartLba;
            output.Emit("progress", "stage", stage, "done", Math.Max(0L, done), "total", (long)(int)progress.SectorCount,
                "elapsedSeconds", (int)progress.ElapsedTime, "remainingSeconds", (int)progress.RemainingTime);
        }

        void RequireMedia(dynamic data, dynamic recorder)
        {
            try
            {
                int unused = (int)data.CurrentPhysicalMediaType;
            }
            catch (COMException e)
            {
                if (e.ErrorCode == NoMediaHResult)
                {
                    throw new HelperError("NO_MEDIA", e.Message.Trim(), e.ErrorCode);
                }
                throw;
            }
            if (!(bool)data.IsCurrentMediaSupported(recorder))
            {
                throw new HelperError("MEDIA_NOT_SUPPORTED", "The disc in the drive cannot be written");
            }
        }

        public void Blank(string drive, bool full)
        {
            dynamic recorder = Imapi.FindRecorder(drive);
            RequireMedia(Imapi.DataWriter(recorder), recorder);
            dynamic erase = Com.Create("IMAPI2.MsftDiscFormat2Erase");
            erase.Recorder = recorder;
            erase.ClientName = "RetroBurn";
            erase.FullErase = full;
            EraseSink sink = new EraseSink((elapsed, estimated) => output.Emit("progress", "stage", "erasing",
                "done", (long)elapsed, "total", (long)estimated, "elapsedSeconds", elapsed));
            using (Events.Connect(erase, Events.EraseEvents, sink))
            {
                // IMAPI2 cannot cancel an erase, so a closed stdin is only noticed afterwards.
                erase.EraseMedia();
            }
        }

        public void VerifyImage(string drive, string imagePath)
        {
            dynamic recorder = Imapi.FindRecorder(drive);
            string volume = Imapi.Volume(recorder);
            if (volume.Length == 0)
            {
                throw new HelperError("DRIVE_NOT_FOUND", "The drive has no drive letter, so the disc cannot be read back");
            }
            long total = new FileInfo(imagePath).Length;
            using (SafeFileHandle handle = Storage.OpenForReading(volume, input))
            using (FileStream disc = new FileStream(handle, FileAccess.Read, SectorSize, false))
            using (FileStream image = File.OpenRead(imagePath))
            {
                byte[] fromDisc = new byte[SectorSize * 32];
                byte[] fromImage = new byte[SectorSize * 32];
                long done = 0;
                Throttle throttle = new Throttle(ProgressIntervalMilliseconds);
                output.Emit("progress", "stage", "verifying", "done", 0L, "total", total);
                while (done < total)
                {
                    input.ThrowIfClosed();
                    int wanted = (int)Math.Min(fromDisc.Length, total - done);
                    int read = disc.Read(fromDisc, 0, wanted);
                    if (read <= 0)
                    {
                        throw new HelperError("VERIFY_MISMATCH", "The disc ends at byte " + done + " of " + total);
                    }
                    ReadFully(image, fromImage, read);
                    for (int i = 0; i < read; i++)
                    {
                        if (fromDisc[i] != fromImage[i])
                        {
                            throw new HelperError("VERIFY_MISMATCH", "First difference at byte " + (done + i));
                        }
                    }
                    done += read;
                    if (throttle.Due())
                    {
                        output.Emit("progress", "stage", "verifying", "done", done, "total", total);
                    }
                }
                output.Emit("progress", "stage", "verifying", "done", done, "total", total);
            }
        }

        static void ReadFully(Stream stream, byte[] buffer, int count)
        {
            int offset = 0;
            while (offset < count)
            {
                int read = stream.Read(buffer, offset, count - offset);
                if (read <= 0)
                {
                    throw new IOException("The image file ended early");
                }
                offset += read;
            }
        }

        public void Eject(string drive)
        {
            Imapi.FindRecorder(drive).EjectMedia();
        }

        public void WatchDrives()
        {
            string last = null;
            do
            {
                List<string> ids = Imapi.DriveIds();
                string signature = string.Join("\n", ids.ToArray());
                if (signature != last)
                {
                    output.Emit("drives", "ids", ids);
                    last = signature;
                }
            }
            while (!input.WaitForClose(2000));
        }

        public void KeepAwake(string reason)
        {
            if (Native.SetThreadExecutionState(Native.EsContinuous | Native.EsSystemRequired) == 0)
            {
                throw new HelperError("IMAPI_ERROR", "Windows refused the keep-awake request");
            }
            output.Log("Keeping the computer awake: " + reason);
            output.Emit("ready", "powershell", "", "clr", Environment.Version.ToString(), "languageMode", "");
            input.WaitForClose(Timeout.Infinite);
            Native.SetThreadExecutionState(Native.EsContinuous);
        }

        public void Power()
        {
            System.Windows.Forms.PowerLineStatus status = System.Windows.Forms.SystemInformation.PowerStatus.PowerLineStatus;
            string source = status == System.Windows.Forms.PowerLineStatus.Online ? "mains"
                : status == System.Windows.Forms.PowerLineStatus.Offline ? "battery"
                : "unknown";
            output.Emit("power", "source", source);
        }
    }

    /// <summary>Finding drives and creating IMAPI2 objects.</summary>
    static class Imapi
    {
        public static List<string> DriveIds()
        {
            object master = Com.Create("IMAPI2.MsftDiscMaster2");
            int count = (int)Com.Get(master, "Count");
            List<string> ids = new List<string>();
            for (int i = 0; i < count; i++)
            {
                ids.Add((string)Com.Get(master, "Item", i));
            }
            return ids;
        }

        public static dynamic Recorder(string id)
        {
            dynamic recorder = Com.Create("IMAPI2.MsftDiscRecorder2");
            recorder.InitializeDiscRecorder(id);
            return recorder;
        }

        public static dynamic FindRecorder(string id)
        {
            if (!DriveIds().Contains(id))
            {
                throw new HelperError("DRIVE_NOT_FOUND", "No drive with id " + id);
            }
            return Recorder(id);
        }

        public static dynamic DataWriter(dynamic recorder)
        {
            dynamic data = Com.Create("IMAPI2.MsftDiscFormat2Data");
            data.Recorder = recorder;
            data.ClientName = "RetroBurn";
            return data;
        }

        /// <summary>The drive letter as "D:", or "" when the drive has none.</summary>
        public static string Volume(dynamic recorder)
        {
            object[] paths = recorder.VolumePathNames as object[];
            if (paths == null)
            {
                return "";
            }
            foreach (object path in paths)
            {
                string text = path as string;
                // Only "X:\" roots; a drive mounted into a folder has no \\.\X: device name.
                if (text != null && text.Length == 3 && char.IsLetter(text[0]) && text.EndsWith(":\\"))
                {
                    return text.Substring(0, 2);
                }
            }
            return "";
        }
    }

    /// <summary>Late-bound COM calls for what C#'s dynamic cannot express (indexed properties).</summary>
    static class Com
    {
        public static dynamic Create(string progId)
        {
            return Activator.CreateInstance(Type.GetTypeFromProgID(progId, true));
        }

        public static object Get(object target, string property, params object[] arguments)
        {
            return target.GetType().InvokeMember(property, BindingFlags.GetProperty, null, target, arguments);
        }

        /// <summary>The real failure behind reflection's wrapper.</summary>
        public static Exception Unwrap(Exception e)
        {
            while (e is TargetInvocationException && e.InnerException != null)
            {
                e = e.InnerException;
            }
            return e;
        }
    }

    static class Streams
    {
        [DllImport("shlwapi.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
        static extern ComTypes.IStream SHCreateStreamOnFileEx(string file, uint mode, uint attributes, bool create, ComTypes.IStream template);

        const uint StgmRead = 0x0;
        const uint StgmShareDenyWrite = 0x20;

        /// <summary>A read-only COM stream over a file, which IMAPI2 reads directly.</summary>
        public static ComTypes.IStream OpenRead(string path)
        {
            return SHCreateStreamOnFileEx(path, StgmRead | StgmShareDenyWrite, 0, false, null);
        }
    }

    /// <summary>Raw volume access: bus type and whole-disc reads.</summary>
    static class Storage
    {
        const uint GenericRead = 0x80000000;
        const uint FileShareReadWrite = 0x1 | 0x2;
        const uint OpenExisting = 3;
        const uint IoctlStorageQueryProperty = 0x002D1400;
        const uint FsctlAllowExtendedDasdIo = 0x00090083;
        const int BusTypeOffset = 28; // STORAGE_DEVICE_DESCRIPTOR.BusType
        const int BusTypeUsb = 7;

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr security, uint disposition,
            uint flags, IntPtr template);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool DeviceIoControl(SafeFileHandle device, uint code, byte[] input, int inputSize, byte[] output,
            int outputSize, out int returned, IntPtr overlapped);

        /// <summary>"usb", "other" or "unknown", from the storage bus the drive is attached to.</summary>
        public static string Bus(string volume)
        {
            if (volume.Length == 0)
            {
                return "unknown";
            }
            // Access 0: a query needs no read permission on the device.
            using (SafeFileHandle device = CreateFile("\\\\.\\" + volume, 0, FileShareReadWrite, IntPtr.Zero,
                OpenExisting, 0, IntPtr.Zero))
            {
                if (device.IsInvalid)
                {
                    return "unknown";
                }
                byte[] query = new byte[12]; // STORAGE_PROPERTY_QUERY: StorageDeviceProperty, PropertyStandardQuery
                byte[] descriptor = new byte[1024];
                int returned;
                if (!DeviceIoControl(device, IoctlStorageQueryProperty, query, query.Length, descriptor,
                    descriptor.Length, out returned, IntPtr.Zero) || returned < BusTypeOffset + 4)
                {
                    return "unknown";
                }
                return BitConverter.ToInt32(descriptor, BusTypeOffset) == BusTypeUsb ? "usb" : "other";
            }
        }

        /// <summary>
        /// Opens the whole disc for reading, waiting up to 60 s for Windows to recognise a fresh disc.
        /// Extended DASD access lets reads go past the ISO 9660 volume into IMAPI2's UDF structures.
        /// </summary>
        public static SafeFileHandle OpenForReading(string volume, Input input)
        {
            int lastError = 0;
            for (int attempt = 0; attempt < 60; attempt++)
            {
                input.ThrowIfClosed();
                SafeFileHandle device = CreateFile("\\\\.\\" + volume, GenericRead, FileShareReadWrite, IntPtr.Zero,
                    OpenExisting, 0, IntPtr.Zero);
                if (!device.IsInvalid)
                {
                    int returned;
                    if (DeviceIoControl(device, FsctlAllowExtendedDasdIo, null, 0, null, 0, out returned, IntPtr.Zero))
                    {
                        return device;
                    }
                    lastError = Marshal.GetLastWin32Error();
                    device.Dispose();
                }
                else
                {
                    lastError = Marshal.GetLastWin32Error();
                }
                Thread.Sleep(1000);
            }
            throw new HelperError("NO_MEDIA", "The disc could not be read (Windows error " + lastError + ")");
        }
    }

    static class Native
    {
        public const uint EsContinuous = 0x80000000;
        public const uint EsSystemRequired = 0x00000001;

        [DllImport("kernel32.dll")]
        public static extern uint SetThreadExecutionState(uint flags);
    }

    /// <summary>Limits how often progress lines are written.</summary>
    sealed class Throttle
    {
        readonly int intervalMilliseconds;
        int last = Environment.TickCount;

        public Throttle(int intervalMilliseconds)
        {
            this.intervalMilliseconds = intervalMilliseconds;
        }

        public bool Due()
        {
            int now = Environment.TickCount;
            if (unchecked(now - last) < intervalMilliseconds)
            {
                return false;
            }
            last = now;
            return true;
        }
    }

    // IMAPI2 event interfaces. IMAPI2 calls them on its own worker thread, which is why they are C#:
    // a PowerShell script block cannot run there.

    [ComImport, Guid("2735413C-7F64-5B0F-8F00-5D77AFBE261E"), InterfaceType(ComInterfaceType.InterfaceIsIDispatch)]
    public interface DDiscFormat2DataEvents
    {
        [DispId(0x200)]
        void Update([MarshalAs(UnmanagedType.IDispatch)] object sender, [MarshalAs(UnmanagedType.IDispatch)] object progress);
    }

    [ComImport, Guid("2735413A-7F64-5B0F-8F00-5D77AFBE261E"), InterfaceType(ComInterfaceType.InterfaceIsIDispatch)]
    public interface DDiscFormat2EraseEvents
    {
        [DispId(0x200)]
        void Update([MarshalAs(UnmanagedType.IDispatch)] object sender, int elapsedSeconds, int estimatedTotalSeconds);
    }

    [ComVisible(true), ClassInterface(ClassInterfaceType.None)]
    public sealed class DataSink : DDiscFormat2DataEvents
    {
        readonly Action<object> onUpdate;

        public DataSink(Action<object> onUpdate)
        {
            this.onUpdate = onUpdate;
        }

        public void Update(object sender, object progress)
        {
            // An exception must never travel back into IMAPI2.
            try
            {
                onUpdate(progress);
            }
            catch (Exception)
            {
            }
        }
    }

    [ComVisible(true), ClassInterface(ClassInterfaceType.None)]
    public sealed class EraseSink : DDiscFormat2EraseEvents
    {
        readonly Action<int, int> onUpdate;

        public EraseSink(Action<int, int> onUpdate)
        {
            this.onUpdate = onUpdate;
        }

        public void Update(object sender, int elapsedSeconds, int estimatedTotalSeconds)
        {
            try
            {
                onUpdate(elapsedSeconds, estimatedTotalSeconds);
            }
            catch (Exception)
            {
            }
        }
    }

    static class Events
    {
        public static readonly Guid DataEvents = new Guid("2735413C-7F64-5B0F-8F00-5D77AFBE261E");
        public static readonly Guid EraseEvents = new Guid("2735413A-7F64-5B0F-8F00-5D77AFBE261E");

        /// <summary>Subscribes a sink to a COM object's events until the connection is disposed.</summary>
        public static Connection Connect(object source, Guid eventInterface, object sink)
        {
            ComTypes.IConnectionPointContainer container = (ComTypes.IConnectionPointContainer)source;
            ComTypes.IConnectionPoint point;
            container.FindConnectionPoint(ref eventInterface, out point);
            int cookie;
            point.Advise(sink, out cookie);
            return new Connection(point, cookie);
        }

        public sealed class Connection : IDisposable
        {
            readonly ComTypes.IConnectionPoint point;
            readonly int cookie;

            public Connection(ComTypes.IConnectionPoint point, int cookie)
            {
                this.point = point;
                this.cookie = cookie;
            }

            public void Dispose()
            {
                point.Unadvise(cookie);
            }
        }
    }
}
