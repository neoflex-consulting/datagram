import sys, os, re, itertools
from operator import itemgetter

if __name__ == "__main__":
    dirs = list(set(sys.argv[1].split(os.pathsep)))
    jars = []
    for dir in dirs:
        for file in os.listdir(dir):
            name, ext = os.path.splitext(file)
            if ext == '.jar':
                match = re.match(r'(.*)-(\d+(\.\d+(\.\d+(\.\d+)?)?)?)-?(.*)', name)
                if match:
                    head = match[1]
                    version = match[2]
                    tail = match.group(match.lastindex)
                else:
                    head = name
                    version = None
                    tail = None
                jars.append((head, list(map(int, version.split('.'))), os.path.join(dir, file), name))
    grouped = [(k, sorted((ll[1], ll[2], ll[3]) for ll in l))
               for k, l in itertools.groupby(sorted(jars, key=itemgetter(0)), itemgetter(0))]
    for k, l in grouped:
        last = l[-1]
        duplicates = [t[1] for t in l[:-1] if t[2] != last[2]]
        if len(duplicates) != 0:
            print(last[1])
            for d in duplicates:
                print("del:", d)
                os.remove(d)
