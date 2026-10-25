/* Dependency-free UI/API integration smoke test with a minimal DOM harness.
 * This checks the UI controller against the live Python server, not visual layout.
 * Run from the project root: node tests/ui_smoke.cjs
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');

async function main() {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(),'findspace-ui-'));
  const root = path.join(temp,'Downloads');
  fs.mkdirSync(root);
  fs.mkdirSync(path.join(root,'project'));
  for (const [name,content] of Object.entries({'Invoice October.pdf':'invoice','Photo.jpg':'picture','Notes.txt':'notes','Notes copy.txt':'notes','project/spec.pdf':'spec','script.py':'print(1)'})) {
    fs.writeFileSync(path.join(root,name),content);
  }
  const python = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
  const child = spawn(python,['app.py','--no-browser','--root',root,'--state-dir',path.join(temp,'state')],{stdio:['ignore','pipe','pipe']});
  let errors = '';
  child.stderr.on('data',c=>errors+=c);
  try {
    const base = await new Promise((resolve,reject)=>{
      const timer = setTimeout(()=>reject(Error('Server startup timeout '+errors)),10000);
      child.stdout.on('data',chunk=>{
        const match = chunk.toString().match(/http:\/\/127\.0\.0\.1:\d+/);
        if(match){clearTimeout(timer);resolve(match[0]);}
      });
      child.on('exit',code=>{clearTimeout(timer);reject(Error('Server exited '+code+' '+errors));});
    });
    const html = await (await fetch(base)).text();
    const token = html.match(/FINDSPACE_TOKEN='([^']+)'/)[1];
    const elements = new Map();
    for(const match of html.matchAll(/<(\w+)[^>]*\bid="([^"]+)"[^>]*>/g)) {
      elements.set(match[2],{
        tagName:match[1].toUpperCase(),value:'',textContent:'',innerHTML:'',hidden:false,disabled:false,checked:false,
        dataset:{},classList:{toggle(){}},focus(){document.activeElement=this;},showModal(){this.open=true;},close(){this.open=false;}
      });
    }
    elements.get('organizeScope').value='loose';
    elements.get('sortFilter').value='recent';
    const document = {
      activeElement:{tagName:'BODY'},
      getElementById:id=>{assert(elements.has(id),'Missing element '+id);return elements.get(id);},
      querySelectorAll:()=>[],querySelector:()=>null,addEventListener(){}
    };
    const context = vm.createContext({
      document,window:{FINDSPACE_TOKEN:token},URLSearchParams,console,
      fetch:(url,options)=>fetch(base+url,options),setTimeout:()=>1,clearTimeout(){}
    });
    vm.runInContext(fs.readFileSync('web/ui.js','utf8'),context);
    const run = expression=>vm.runInContext(expression,context);
    const wait = async()=>{
      for(let i=0;i<100;i++){
        await new Promise(r=>setTimeout(r,30));
        await run('poll()');
        if(!run('state.busy'))return;
      }
      throw Error('Operation timeout');
    };
    await wait();
    await run('renderView()');
    assert.equal(elements.get('statCount').textContent,'6');
    assert(elements.get('recentFiles').innerHTML.includes('file-table'));
    run("view='files'; $('searchInput').value='invoice pdf'");
    await run('renderView()');
    assert.equal(elements.get('resultCount').textContent,'1 file found');
    assert(elements.get('fileResults').innerHTML.includes('Invoice October.pdf'));
    run("$('searchInput').value=''; $('kindFilter').value='Images'");
    await run('renderView()');
    assert(elements.get('fileResults').innerHTML.includes('Photo.jpg'));
    assert(!elements.get('fileResults').innerHTML.includes('Invoice October.pdf'));
    run("view='space'");await run('renderView()');
    assert(elements.get('categoryBreakdown').innerHTML.includes('Documents'));
    run("view='duplicates'");await elements.get('duplicatesBtn').onclick();await wait();
    await run('renderView()');
    assert(elements.get('duplicateResults').innerHTML.includes('1 exact duplicate group'));
    run("view='organize'");await elements.get('previewBtn').onclick();
    assert(elements.get('planHeading').textContent.includes('4 proposed moves'));
    assert.equal(elements.get('selectedCount').textContent.startsWith('4 files selected'),true);
    const applying = elements.get('applyBtn').onclick();
    assert(elements.get('confirmDialog').open);
    elements.get('acceptConfirm').onclick();
    await applying;await wait();
    assert(fs.existsSync(path.join(root,'Findspace Organized','Documents','Invoice October.pdf')));
    assert(fs.existsSync(path.join(root,'project','spec.pdf')));
    assert(fs.existsSync(path.join(root,'script.py')));
    await run('renderView()');
    assert(elements.get('history').innerHTML.includes('Undo moves'));
    await run("api('undo',{batch:state.history[0].batch})");await wait();
    assert(fs.existsSync(path.join(root,'Invoice October.pdf')));
    assert(!fs.existsSync(path.join(root,'Findspace Organized','Documents','Invoice October.pdf')));
    assert.equal(errors,'');
    console.log('PASS: overview, search, type filter, storage, exact duplicates, preview, confirmation, move history and undo through the live local API.');
  } finally {
    child.kill();
    await new Promise(r=>child.exitCode!==null?r():child.once('exit',r));
    fs.rmSync(temp,{recursive:true,force:true});
  }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
