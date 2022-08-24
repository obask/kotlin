/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

const vm = require('vm');
const fs = require('fs');
const readline = require('readline');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false,
    prompt: 'Enter path to a script to run:'
});

const main = async () => {
    for await (const testFilePath of rl) {
        const sandbox = {};
        vm.createContext(sandbox);
        const code = fs.readFileSync(testFilePath, 'utf-8');
        debugger;
        try {
            vm.runInContext(code, sandbox, testFilePath);
            vm.runInContext('main.box()', sandbox);
        } catch (e) {
            
        }
    }    
};

main();
