const express = require('express');
const auth = require('../middleware/auth');
const Account = require('../models/account');
const Note = require('../models/note');

const router = express.Router();

const SAFE_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

function sanitizeString(val, maxLen = 1000) {
    if (typeof val !== 'string') {
        return null;
    }
    const trimmed = val.trim();
    if (!trimmed || trimmed.length > maxLen) {
        return null;
    }
    return String(trimmed);
}

// Keeps only allowed primitive fields and blocks MongoDB '$' or '.' keys
function sanitizeAccountBody(body) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
        return {};
    }

    const allowedFields = ['username', 'email', 'password', 'name'];
    const clean = {};

    for (const key of allowedFields) {
        if (!Object.prototype.hasOwnProperty.call(body, key)) {
            continue;
        }
        const val = body[key];
        if (typeof val === 'string') {
            clean[key] = String(val);
        }
    }

    return clean;
}

function sanitizeNoteBody(body) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
        return {};
    }

    const clean = {};
    if (typeof body.title === 'string') {
        clean.title = String(body.title);
    }
    if (typeof body.content === 'string') {
        clean.content = String(body.content);
    }
    if (typeof body.body === 'string') {
        clean.body = String(body.body);
    }

    return clean;
}

function isValidParam(param) {
    return typeof param === 'string' && SAFE_ID_REGEX.test(param);
}

router.post('/accounts', async (req, res) => {
    try {
        const cleanAccountData = sanitizeAccountBody(req.body);
        const account = new Account(cleanAccountData);
        await account.save();

        return res.status(201).end();
    } catch (e) {
        let status = 400;
        let error = 'Failed to create account';

        if (e && e.code === 11000) {
            status = 409;
            error = 'Email address is already registered';
        }

        return res.status(status).json({ error });
    }
});

router.put('/accounts/:username/notes/:note', auth, async (req, res) => {
    const rawUsername = req.params.username;
    const rawNote = req.params.note;

    if (!isValidParam(rawUsername) || !isValidParam(rawNote)) {
        return res.status(400).json({ error: 'Invalid username or note ID' });
    }

    const username = String(rawUsername.replace(/[^a-zA-Z0-9_-]/g, ''));
    const noteId = String(rawNote.replace(/[^a-zA-Z0-9_-]/g, ''));

    if (!username || !noteId) {
        return res.status(400).json({ error: 'Invalid username or note ID' });
    }

    const cleanNoteBody = sanitizeNoteBody(req.body);
        return res.status(500).json({ error: 'Failed to create/update note' });
    }<([{newLine}]>});<([{newLine}]><([{newLine}]>router.get('/accounts/:username/notes', auth, async (req, res, next) => {

    try {
        await Note.findOneAndUpdate(
            { owner: { $eq: username }, id: { $eq: noteId } },
            { $set: updatePayload },
            { new: true, upsert: true, runValidators: true }
        ).exec();

        return res.status(204).end();
    } catch (e) {
        return res.status(500).json({ error: 'Failed to create/update note' });
    }
});

router.get('/accounts/:username/notes', auth, async (req, res) => {
    const rawUsername = req.params.username;

    if (!isValidParam(rawUsername)) {
        return res.status(400).json({ error: 'Invalid username' });
    }

    const username = String(rawUsername.replace(/[^a-zA-Z0-9_-]/g, ''));
    if (!username) {
        return res.status(400).json({ error: 'Invalid username' });
    }

    try {
        const notes = await Note.find(
            { owner: { $eq: username } },
            null,
            { lean: true }
        ).exec();

        return res.status(200).json(notes);
    } catch (e) {
        return res.status(500).json({ error: 'Failed to retrieve notes' });
    }
});

module.exports = router;