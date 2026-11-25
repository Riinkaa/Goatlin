const express = require('express');
const auth = require('../middleware/auth');
const Account = require('../models/account');
const Note = require('../models/note');

const router = express.Router();

// Strips disallowed characters and confirms the URL parameter is a safe plain string
function getSafeParam(value) {
    if (typeof value !== 'string') {
        return null;
    }

    const sanitized = value.replace(/[^a-zA-Z0-9@._-]/g, '');
    if (!sanitized || sanitized !== value) {
        return null;
    }

    return sanitized;
}

// Keeps only plain values and blocks MongoDB '$' operators or nested objects
function sanitizeBody(body) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
        return {};
    }

    const clean = {};
    for (const [key, value] of Object.entries(body)) {
        if (
            key.startsWith('$') ||
            key.includes('.') ||
            key === '__proto__' ||
            key === 'constructor' ||
            key === 'prototype'
        ) {
            continue;
        }
        if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
            clean[key] = value;
        }
    }
    return clean;
}

router.post('/accounts', async (req, res, next) => {
    try {
        const cleanAccountData = sanitizeBody(req.body);
        const account = new Account(cleanAccountData);
        await account.save();

        return res.status(201).end();
    } catch (e) {
        let status = 404;
        let error = 'Failed to create account';

        if (e.name === 'MongoError' && e.code === 11000) {
            status = 409;
            error = 'Email address is already registered';
        }

        return res.status(status).json({ error });
    }
});

router.put('/accounts/:username/notes/:note', auth, async (req, res, next) => {
    const username = getSafeParam(req.params.username);
    const noteId = getSafeParam(req.params.note);

    if (!username || !noteId) {
        return res.status(400).json({ error: 'Invalid username or note ID' });
    }

    const cleanNoteBody = sanitizeBody(req.body);
    delete cleanNoteBody.id;
    delete cleanNoteBody.owner;

    const safeNote = {
        ...cleanNoteBody,
        id: noteId,
        owner: username
    };

    try {
        await Note.findOneAndUpdate(
            { owner: { $eq: String(username) }, id: { $eq: String(noteId) } },
            { $set: safeNote },
            { new: true, upsert: true }
        );

        return res.status(204).end();
    } catch (e) {
        console.log(e);
        return res.status(500).json({ error: 'Failed to create/update note' });
    }
});

router.get('/accounts/:username/notes', auth, async (req, res, next) => {
    const username = getSafeParam(req.params.username);

    if (!username) {
        return res.status(400).json({ error: 'Invalid username' });
    }

    try {
        const notes = await Note.find(
            { owner: { $eq: String(username) } },
            null,
            { lean: true }
        ).exec();

        return res.status(200).json(notes);
    } catch (e) {
        console.error(e);
        return res.status(500).json({ error: e.message });
    }
});

module.exports = router;