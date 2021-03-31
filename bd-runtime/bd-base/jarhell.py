import sys, os, re, itertools, fnmatch
from operator import itemgetter


def collect_file(jars, dir, file):
    name, ext = os.path.splitext(file)
    match = re.match(r'(.*?)-(\d+(\.\d+(\.\d+(\.\d+)?)?)?)-?(.*)', name)
    if match:
        head = match[1]
        version = match[2]
        vlist = list(map(int, version.split('.')))
        tail = match.group(match.lastindex)
        # vlist.append(tail)
        jars.append((head, vlist, os.path.join(dir, file), name))


def collect_dir(jars, dir, mask):
    for file in os.listdir(dir):
        if fnmatch.fnmatch(file, mask):
            collect_file(jars, dir, file)


if __name__ == "__main__":
    dirs = list(set(sys.argv[1].split(os.pathsep)))
    jars = []
    for dir in dirs:
        if os.path.isdir(dir):
            collect_dir(jars, dir, "*.jar")
        else:
            parent = os.path.dirname(dir)
            if os.path.isdir(parent):
                collect_dir(jars, parent, os.path.basename(dir))
    grouped = [(k, sorted((ll[1], ll[2], ll[3]) for ll in l))
               for k, l in itertools.groupby(sorted(jars, key=itemgetter(0)), itemgetter(0))]
    for k, l in grouped:
        last = l[-1]
        duplicates = [t[1] for t in l[:-1] if t[2] != last[2]]
        if len(duplicates) != 0:
            print(last[1])
            for d in duplicates:
                print("del:", d)
                if len(sys.argv) <= 2 or sys.argv[2] != "test":
                    os.remove(d)
