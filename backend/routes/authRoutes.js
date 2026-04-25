const express = require('express');
const router = express.Router();
const auth = require('../controllers/authController');
const verifyToken = require('../middleware/authMiddleware');

router.post('/register', auth.register);
router.get('/verify/:token', auth.verifyEmail);
router.post('/login', auth.login);
router.post('/reset-password', verifyToken, auth.resetPassword);

module.exports = router;